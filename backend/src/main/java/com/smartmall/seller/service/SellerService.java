package com.smartmall.seller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.PageParams;
import com.smartmall.common.PageResult;
import com.smartmall.notification.service.NotificationService;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.dto.ShipRequest;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卖家中心：卖家只能发布和修改自己的商品。
 * 管理员走 AdminService，这里是 SELLER 角色的自服务通道，所有写操作都校验 seller_id 归属。
 *
 * 订单按卖家拆分：order_item 上固化了 seller_id 与 ship_status，
 * 卖家只能看到并操作「含自己商品」的订单，且只更新属于自己那部分子项。
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private final ProductMapper productMapper;
    private final ProductService productService;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;

    /** 卖家商品列表：只返回自己发布的商品 */
    public PageResult<ProductVO> myProducts(long pageNo, long size, String keyword, Integer status) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        CurrentUser user = currentSeller();
        Page<Product> page = productMapper.selectPage(
                new Page<>(pageNo, size),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getSellerId, user.id())
                        .like(StringUtils.hasText(keyword), Product::getName, keyword)
                        .eq(status != null, Product::getStatus, status)
                        .orderByDesc(Product::getCreatedAt));
        Map<String, com.smartmall.product.entity.Category> categories = productService.categoryMap();
        List<ProductVO> records = page.getRecords().stream()
                .map(p -> productService.toVO(p, categories, null, null))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public ProductVO publish(AdminProductRequest request) {
        CurrentUser user = currentSeller();
        Product product = new Product();
        product.setId(Ids.next());
        apply(product, request);
        product.setSellerId(user.id());
        product.setSales(0);
        product.setRating(BigDecimal.valueOf(5.0));
        product.setReviewCount(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    @Transactional
    public ProductVO update(String id, AdminProductRequest request) {
        Product product = requireOwnProduct(id);
        apply(product, request);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    /** 上下架：卖家只能操作自己的商品 */
    @Transactional
    public ProductVO toggleStatus(String id, Integer status) {
        Product product = requireOwnProduct(id);
        product.setStatus(status != null && status == 0 ? 0 : 1);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    /**
     * 删除商品：只允许删除自己的、且没有产生过订单的商品。
     * 有订单的商品直接删除会破坏历史数据，改为提示下架。
     */
    @Transactional
    public void delete(String id) {
        Product product = requireOwnProduct(id);
        Long sold = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getProductId, id));
        if (sold != null && sold > 0) {
            throw new BizException("该商品已产生订单，不能删除，请改为下架");
        }
        productMapper.deleteById(product.getId());
    }

    /** 卖家概览：商品数、在售数、已售件数 */
    public Map<String, Object> overview() {
        CurrentUser user = currentSeller();
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, user.id()));
        long onSale = products.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
        long sold = products.stream().mapToLong(p -> p.getSales() == null ? 0 : p.getSales()).sum();
        BigDecimal stock = products.stream()
                .map(p -> BigDecimal.valueOf(p.getStock() == null ? 0 : p.getStock()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", products.size());
        result.put("onSaleCount", onSale);
        result.put("soldCount", sold);
        result.put("stockTotal", stock);

        // 待发货子项数与涉及订单数，用于首页提醒
        List<OrderItem> pendingItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getSellerId, user.id())
                .eq(OrderItem::getShipStatus, OrderItem.SHIP_PENDING));
        Set<String> pendingOrders = new LinkedHashSet<>();
        long pendingUnits = 0;
        for (OrderItem item : pendingItems) {
            Order order = orderMapper.selectById(item.getOrderId());
            // 已取消/关闭的订单不计入待发货
            if (order == null || OrderStatus.CANCELLED.equals(order.getStatus())
                    || OrderStatus.CLOSED.equals(order.getStatus())
                    || OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
                continue;
            }
            pendingOrders.add(item.getOrderId());
            pendingUnits += item.getQuantity() == null ? 0 : item.getQuantity();
        }
        result.put("pendingShipOrders", pendingOrders.size());
        result.put("pendingShipItems", pendingUnits);
        return result;
    }

    /**
     * 卖家订单列表：只看含自己商品的订单，按子项发货状态筛选。
     * shipStatus 取值 PENDING（待发货）/ SHIPPED（已发货）/ 空（全部）。
     */
    public PageResult<Map<String, Object>> orders(String shipStatus, long pageNo, long size) {
        CurrentUser user = currentSeller();

        // 先找出属于我的子项，据此定位相关订单
        LambdaQueryWrapper<OrderItem> itemQuery = new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getSellerId, user.id());
        if (StringUtils.hasText(shipStatus)) {
            itemQuery.eq(OrderItem::getShipStatus, shipStatus);
        }
        List<OrderItem> myItems = orderItemMapper.selectList(itemQuery);
        if (myItems.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNo, size);
        }

        // 按订单聚合，同一订单只出现一次
        Map<String, List<OrderItem>> byOrder = new LinkedHashMap<>();
        for (OrderItem item : myItems) {
            byOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }

        List<Order> orders = orderMapper.selectBatchIds(byOrder.keySet());
        // 已取消/已关闭的订单不再出现在待处理列表
        orders.removeIf(o -> OrderStatus.CANCELLED.equals(o.getStatus())
                || OrderStatus.CLOSED.equals(o.getStatus()));
        orders.sort(Comparator.comparing(Order::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = orders.size();
        long safeSize = Math.max(1, Math.min(size, 50));
        int from = (int) Math.min((Math.max(1, pageNo) - 1) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order order : orders.subList(from, to)) {
            rows.add(toSellerOrderRow(order, byOrder.get(order.getId())));
        }
        return PageResult.of(rows, total, pageNo, safeSize);
    }

    /** 卖家订单详情：只允许查看含自己商品的订单 */
    public Map<String, Object> orderDetail(String orderId) {
        CurrentUser user = currentSeller();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        List<OrderItem> myItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getSellerId, user.id()));
        if (myItems.isEmpty()) {
            throw new BizException(403, "只能查看自己商品的订单");
        }
        Map<String, Object> row = toSellerOrderRow(order, myItems);
        row.put("items", myItems.stream().map(this::toItemRow).toList());
        return row;
    }

    /**
     * 卖家发货：只把属于自己且待发货的子项标记为已发货，
     * 随后按全部子项聚合回写主订单状态（全部发完才算整单已发货）。
     */
    @Transactional
    public Map<String, Object> ship(String orderId, ShipRequest request) {
        CurrentUser user = currentSeller();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (OrderStatus.CANCELLED.equals(order.getStatus()) || OrderStatus.CLOSED.equals(order.getStatus())) {
            throw new BizException("订单已取消或已关闭，无法发货");
        }

        List<OrderItem> myItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getSellerId, user.id()));
        if (myItems.isEmpty()) {
            throw new BizException(403, "只能对自己商品的订单发货");
        }
        List<OrderItem> pending = myItems.stream()
                .filter(i -> !OrderItem.SHIP_SHIPPED.equals(i.getShipStatus()))
                .toList();
        if (pending.isEmpty()) {
            throw new BizException("该订单中你的商品已全部发货");
        }

        String carrier = request != null && StringUtils.hasText(request.getCarrier())
                ? request.getCarrier() : "顺丰速运";
        String trackingNo = request != null && StringUtils.hasText(request.getTrackingNo())
                ? request.getTrackingNo() : "SF" + System.currentTimeMillis();

        LocalDateTime now = LocalDateTime.now();
        for (OrderItem item : pending) {
            item.setShipStatus(OrderItem.SHIP_SHIPPED);
            item.setCarrier(carrier);
            item.setTrackingNo(trackingNo);
            item.setShippedAt(now);
            orderItemMapper.updateById(item);
        }

        // 聚合主订单状态：所有子项都发货了才把整单置为已发货
        refreshOrderShipStatus(order);

        notificationService.notify(order.getUserId(), "ORDER_SHIPPED", "你的订单已发货",
                "订单 " + order.getOrderNo() + " 中的部分商品已由 " + carrier + " 发出，运单号 "
                        + trackingNo + "。",
                "/order/" + order.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("shippedCount", pending.size());
        result.put("carrier", carrier);
        result.put("trackingNo", trackingNo);
        result.put("orderStatus", order.getStatus());
        return result;
    }

    /** 按子项聚合同步主订单的发货状态 */
    private void refreshOrderShipStatus(Order order) {
        List<OrderItem> all = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        boolean allShipped = !all.isEmpty()
                && all.stream().allMatch(i -> OrderItem.SHIP_SHIPPED.equals(i.getShipStatus()));
        if (allShipped && OrderStatus.PAID.equals(order.getStatus())) {
            order.setStatus(OrderStatus.SHIPPED);
            order.setShippedAt(LocalDateTime.now());
            orderMapper.updateById(order);
        }
    }

    private Map<String, Object> toSellerOrderRow(Order order, List<OrderItem> myItems) {
        List<OrderItem> items = myItems == null ? List.of() : myItems;
        BigDecimal myAmount = items.stream()
                .map(i -> (i.getProductPrice() == null ? BigDecimal.ZERO : i.getProductPrice())
                        .multiply(BigDecimal.valueOf(i.getQuantity() == null ? 0 : i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allShipped = !items.isEmpty()
                && items.stream().allMatch(i -> OrderItem.SHIP_SHIPPED.equals(i.getShipStatus()));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("orderStatus", order.getStatus());
        row.put("orderStatusLabel", OrderStatus.label(order.getStatus()));
        row.put("shipStatus", allShipped ? OrderItem.SHIP_SHIPPED : OrderItem.SHIP_PENDING);
        row.put("shipStatusLabel", allShipped ? "已发货" : "待发货");
        row.put("receiver", order.getReceiver());
        row.put("phone", order.getPhone());
        row.put("addressText", order.getAddressText());
        row.put("remark", order.getRemark());
        row.put("deliveryMethod", order.getDeliveryMethod());
        row.put("createdAt", order.getCreatedAt());
        row.put("paidAt", order.getPaidAt());
        row.put("myItemCount", items.stream()
                .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum());
        row.put("myAmount", myAmount);
        row.put("carrier", items.stream().map(OrderItem::getCarrier)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        row.put("trackingNo", items.stream().map(OrderItem::getTrackingNo)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        return row;
    }

    private Map<String, Object> toItemRow(OrderItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("productId", item.getProductId());
        row.put("productName", item.getProductName());
        row.put("productImage", item.getProductImage());
        row.put("quantity", item.getQuantity());
        row.put("price", item.getProductPrice());
        row.put("amount", (item.getProductPrice() == null ? BigDecimal.ZERO : item.getProductPrice())
                .multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity())));
        row.put("shipStatus", item.getShipStatus());
        row.put("carrier", item.getCarrier());
        row.put("trackingNo", item.getTrackingNo());
        row.put("shippedAt", item.getShippedAt());
        return row;
    }

    private CurrentUser currentSeller() {
        CurrentUser user = AuthContext.require();
        if (!user.isSeller() && !user.isAdmin()) {
            throw new BizException(403, "当前账号不是卖家，无法进入卖家中心");
        }
        return user;
    }

    /** 归属校验：商品必须存在且属于当前卖家（管理员可越过归属限制） */
    private Product requireOwnProduct(String id) {
        CurrentUser user = currentSeller();
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!user.isAdmin() && !user.id().equals(product.getSellerId())) {
            throw new BizException(403, "只能管理自己发布的商品");
        }
        return product;
    }

    private void apply(Product product, AdminProductRequest request) {
        product.setName(request.getName());
        product.setSubtitle(request.getSubtitle());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock() == null ? 0 : request.getStock());
        product.setDescription(request.getDescription());
        product.setAttributes(com.smartmall.common.JsonUtils.toJson(
                request.getAttributes() == null ? new LinkedHashMap<>() : request.getAttributes()));
        product.setTags(request.getTags());
        // 卖家没有上传能力，未填图片时用品类占位图，避免空图
        product.setImage(StringUtils.hasText(request.getImage())
                ? request.getImage()
                : "/img/default.svg");
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}