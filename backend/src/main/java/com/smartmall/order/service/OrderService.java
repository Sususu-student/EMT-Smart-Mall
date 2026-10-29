package com.smartmall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.auth.AuthContext;
import com.smartmall.cart.entity.CartItem;
import com.smartmall.cart.mapper.CartItemMapper;
import com.smartmall.cart.service.CartService;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.PageParams;
import com.smartmall.common.PageResult;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.dto.CreateOrderRequest;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.user.entity.UserAddress;
import com.smartmall.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final CartService cartService;
    private final ProductMapper productMapper;
    private final UserAddressMapper addressMapper;
    private final com.smartmall.notification.service.NotificationService notificationService;

    @Value("${app.order.unpaid-timeout-minutes:30}")
    private long unpaidTimeoutMinutes;

    @Transactional
    public OrderVO create(CreateOrderRequest request) {
        String userId = AuthContext.userId();
        UserAddress address = resolveAddress(userId, request.getAddressId());

        List<OrderItem> items = new ArrayList<>();
        List<String> cartProductIds = new ArrayList<>();
        if (StringUtils.hasText(request.getProductId())) {
            Product product = requireProduct(request.getProductId());
            int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
            items.add(buildItem(product, quantity));
        } else {
            List<CartItem> cartItems = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getUserId, userId)
                    .eq(CartItem::getSelected, 1)
                    .in(request.getCartItemIds() != null && !request.getCartItemIds().isEmpty(),
                            CartItem::getId, request.getCartItemIds()));
            if (cartItems.isEmpty()) {
                throw new BizException("请先选择要结算的商品");
            }
            for (CartItem cartItem : cartItems) {
                Product product = requireProduct(cartItem.getProductId());
                items.add(buildItem(product, cartItem.getQuantity()));
                cartProductIds.add(cartItem.getProductId());
            }
        }

        BigDecimal goodsTotal = items.stream()
                .map(i -> i.getProductPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String deliveryMethod = "EXPRESS".equalsIgnoreCase(request.getDeliveryMethod())
                ? "EXPRESS" : "STANDARD";
        BigDecimal deliveryFee = "EXPRESS".equals(deliveryMethod) ? BigDecimal.valueOf(12) : BigDecimal.ZERO;
        BigDecimal total = goodsTotal.add(deliveryFee);

        // 先锁库存，避免超卖
        for (OrderItem item : items) {
            deductStock(item.getProductId(), item.getQuantity());
        }

        Order order = new Order();
        order.setId(Ids.next());
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setAddressId(address.getId());
        order.setReceiver(address.getReceiver());
        order.setPhone(address.getPhone());
        order.setAddressText(address.fullAddress());
        order.setRemark(request.getRemark());
        order.setDeliveryMethod(deliveryMethod);
        order.setDeliveryFee(deliveryFee);
        order.setCreatedAt(LocalDateTime.now());
        order.setExpiredAt(LocalDateTime.now().plusMinutes(unpaidTimeoutMinutes));
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        if (!cartProductIds.isEmpty()) {
            cartService.removeByProductIds(userId, cartProductIds);
        }
        return toVO(order, items);
    }

    public PageResult<OrderVO> list(String status, long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 60);
        pageNo = checked[0];
        size = checked[1];
        String userId = AuthContext.userId();
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNo, size),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(StringUtils.hasText(status), Order::getStatus, status)
                        .orderByDesc(Order::getCreatedAt));
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public OrderVO detail(String id) {
        Order order = requireOwnOrder(id);
        return toVO(order, itemsOf(order.getId()));
    }

    public OrderVO detailForUser(String userId, String id) {
        Order order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.notFound("订单不存在");
        }
        return toVO(order, itemsOf(order.getId()));
    }

    public List<OrderVO> recentForAgent(String userId, int limit) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 10))));
        return toVOs(orders);
    }

    public Order findByOrderNo(String userId, String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getOrderNo, orderNo)
                .last("LIMIT 1"));
    }

    @Transactional
    public OrderVO pay(String id) {
        Order order = requireOwnOrder(id);
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BizException("当前订单状态不可支付");
        }
        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(LocalDateTime.now())) {
            close(order);
            throw new BizException("订单已超时关闭，请重新下单");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setPaymentNo("MOCK-" + order.getOrderNo());
        order.setPaymentChannel("MOCK");
        orderMapper.updateById(order);
        for (OrderItem item : itemsOf(order.getId())) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("sales = sales + " + item.getQuantity()));
        }
        notificationService.notify(order.getUserId(), "PAYMENT_SUCCESS", "支付成功",
                "订单 " + order.getOrderNo() + " 已支付 " + order.getTotalAmount() + " 元，商家正在备货。",
                "/order/" + order.getId());
        return toVO(order, itemsOf(order.getId()));
    }

    @Transactional
    public OrderVO cancel(String id) {
        Order order = requireOwnOrder(id);
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BizException("仅待支付订单可以取消");
        }
        close(order);
        return toVO(order, itemsOf(order.getId()));
    }

    @Transactional
    public OrderVO confirmReceipt(String id) {
        Order order = requireOwnOrder(id);
        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            throw new BizException("当前订单状态不可确认收货");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setFinishedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return toVO(order, itemsOf(order.getId()));
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void closeExpiredOrders() {
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT)
                .lt(Order::getExpiredAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (Order order : expired) {
            close(order);
            log.info("订单 {} 超时关闭，库存已回滚", order.getOrderNo());
        }
    }

    private void close(Order order) {
        for (OrderItem item : itemsOf(order.getId())) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("stock = stock + " + item.getQuantity()));
        }
        order.setStatus(OrderStatus.CLOSED);
        orderMapper.updateById(order);
    }

    private void deductStock(String productId, int quantity) {
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .ge(Product::getStock, quantity)
                .setSql("stock = stock - " + quantity));
        if (rows == 0) {
            throw new BizException("商品库存不足，请调整购买数量");
        }
    }

    private Product requireProduct(String productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw BizException.notFound("商品不存在或已下架");
        }
        return product;
    }

    private UserAddress resolveAddress(String userId, String addressId) {
        UserAddress address;
        if (StringUtils.hasText(addressId)) {
            address = addressMapper.selectById(addressId);
            if (address == null || !address.getUserId().equals(userId)) {
                throw BizException.notFound("收货地址不存在");
            }
        } else {
            address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getIsDefault)
                    .last("LIMIT 1"));
        }
        if (address == null) {
            throw new BizException("请先添加收货地址");
        }
        return address;
    }

    private OrderItem buildItem(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new BizException("「" + product.getName() + "」库存不足");
        }
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage(product.getImage());
        item.setProductPrice(product.getPrice());
        item.setQuantity(quantity);
        // 固化发货归属：平台自营商品没有卖家，用占位标识，便于卖家侧查询
        item.setSellerId(StringUtils.hasText(product.getSellerId())
                ? product.getSellerId() : OrderItem.PLATFORM_SELLER);
        item.setShipStatus(OrderItem.SHIP_PENDING);
        item.setRefundStatus("NONE");
        return item;
    }

    private Order requireOwnOrder(String id) {
        Order order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    private List<OrderItem> itemsOf(String orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
    }

    private List<OrderVO> toVOs(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> ids = orders.stream().map(Order::getId).toList();
        Map<String, List<OrderItem>> grouped = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, ids)).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .map(o -> toVO(o, grouped.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.label(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setReceiver(order.getReceiver());
        vo.setPhone(order.getPhone());
        vo.setAddressText(order.getAddressText());
        vo.setRemark(order.getRemark());
        vo.setCarrier(order.getCarrier());
        vo.setTrackingNo(order.getTrackingNo());
        vo.setPaymentNo(order.getPaymentNo());
        vo.setPaymentChannel(order.getPaymentChannel());
        vo.setDeliveryMethod(order.getDeliveryMethod());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setPaidAt(order.getPaidAt());
        vo.setShippedAt(order.getShippedAt());
        vo.setFinishedAt(order.getFinishedAt());
        vo.setExpiredAt(order.getExpiredAt());
        vo.setItems(items);
        vo.setTotalQuantity(items.stream().mapToInt(OrderItem::getQuantity).sum());
        return vo;
    }

    private String generateOrderNo() {
        return "SG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    public PageResult<OrderVO> adminList(String keyword, String status, long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 60);
        pageNo = checked[0];
        size = checked[1];
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNo, size),
                new LambdaQueryWrapper<Order>()
                        .and(StringUtils.hasText(keyword), w -> w.like(Order::getOrderNo, keyword)
                                .or().like(Order::getReceiver, keyword))
                        .eq(StringUtils.hasText(status), Order::getStatus, status)
                        .orderByDesc(Order::getCreatedAt));
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public OrderVO adminShip(String id, com.smartmall.order.dto.ShipRequest request) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!OrderStatus.PAID.equals(order.getStatus())) {
            throw new BizException("仅已支付订单可以发货");
        }
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        if (request != null) {
            order.setCarrier(StringUtils.hasText(request.getCarrier()) ? request.getCarrier() : "顺丰速运");
            order.setTrackingNo(StringUtils.hasText(request.getTrackingNo())
                    ? request.getTrackingNo()
                    : "SF" + System.currentTimeMillis());
        } else {
            order.setCarrier("顺丰速运");
            order.setTrackingNo("SF" + System.currentTimeMillis());
        }
        orderMapper.updateById(order);
        notificationService.notify(order.getUserId(), "ORDER_SHIPPED", "订单已发货",
                "订单 " + order.getOrderNo() + " 已由 " + order.getCarrier() + " 发出，运单号 "
                        + order.getTrackingNo() + "。",
                "/order/" + order.getId());
        return toVO(order, itemsOf(order.getId()));
    }
}
