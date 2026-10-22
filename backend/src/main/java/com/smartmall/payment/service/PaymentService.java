package com.smartmall.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartmall.auth.AuthContext;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.notification.service.NotificationService;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.payment.entity.PaymentRecord;
import com.smartmall.payment.mapper.PaymentRecordMapper;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRecordMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final NotificationService notificationService;

    @Transactional
    public PaymentRecord create(String orderId, String channel) {
        String userId = AuthContext.userId();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.notFound("订单不存在");
        }
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BizException("当前订单状态不可支付");
        }
        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BizException("订单已超时关闭，请重新下单");
        }
        PaymentRecord pending = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getStatus, "PENDING")
                .orderByDesc(PaymentRecord::getCreatedAt)
                .last("LIMIT 1"));
        if (pending != null) {
            return pending;
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setId(Ids.next());
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(order.getId());
        payment.setUserId(userId);
        payment.setAmount(order.getTotalAmount());
        payment.setChannel(StringUtils.hasText(channel) ? channel : "ALIPAY");
        payment.setStatus("PENDING");
        payment.setCreatedAt(LocalDateTime.now());
        paymentMapper.insert(payment);

        order.setPaymentNo(payment.getPaymentNo());
        order.setPaymentChannel(payment.getChannel());
        orderMapper.updateById(order);
        return payment;
    }

    /**
     * 模拟支付网关回调。真实项目应校验签名、金额和幂等键。
     */
    @Transactional
    public PaymentRecord callback(String paymentNo, Boolean success) {
        PaymentRecord payment = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, paymentNo)
                .last("LIMIT 1"));
        if (payment == null) {
            throw BizException.notFound("支付单不存在");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            return payment;
        }
        if (Boolean.FALSE.equals(success)) {
            payment.setStatus("FAILED");
            paymentMapper.updateById(payment);
            return payment;
        }
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (OrderStatus.PAID.equals(order.getStatus())) {
            payment.setStatus("SUCCESS");
            payment.setPaidAt(LocalDateTime.now());
            paymentMapper.updateById(payment);
            return payment;
        }
        if (!OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BizException("订单状态不可支付");
        }
        if (order.getExpiredAt() != null && order.getExpiredAt().isBefore(LocalDateTime.now())) {
            payment.setStatus("FAILED");
            paymentMapper.updateById(payment);
            throw new BizException("订单已超时，支付回调被拒绝");
        }
        payment.setStatus("SUCCESS");
        payment.setPaidAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(payment.getPaidAt());
        order.setPaymentNo(payment.getPaymentNo());
        order.setPaymentChannel(payment.getChannel());
        orderMapper.updateById(order);

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("sales = sales + " + item.getQuantity()));
        }
        notificationService.notify(order.getUserId(), "PAYMENT_SUCCESS", "支付成功",
                "订单 " + order.getOrderNo() + " 已支付 " + payment.getAmount() + " 元，商家正在备货。",
                "/order/" + order.getId());
        return payment;
    }

    public PaymentRecord get(String paymentNo) {
        PaymentRecord payment = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, paymentNo)
                .last("LIMIT 1"));
        if (payment == null || !payment.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("支付单不存在");
        }
        return payment;
    }

    public List<PaymentRecord> byOrder(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("订单不存在");
        }
        return paymentMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .orderByDesc(PaymentRecord::getCreatedAt));
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
