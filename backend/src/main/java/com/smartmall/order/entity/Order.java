package com.smartmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.INPUT)
    private String id;
    private String orderNo;
    private String userId;
    private BigDecimal totalAmount;
    private String status;
    private String addressId;
    private String receiver;
    private String phone;
    private String addressText;
    private String remark;
    private String carrier;
    private String trackingNo;
    private String paymentNo;
    private String paymentChannel;
    private String deliveryMethod;
    private BigDecimal deliveryFee;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiredAt;
}
