package com.smartmall.order.dto;

import com.smartmall.order.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private String id;
    private String orderNo;
    private String status;
    private String statusLabel;
    private BigDecimal totalAmount;
    private String receiver;
    private String phone;
    private String addressText;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiredAt;
    private List<OrderItem> items;
    private int totalQuantity;
}
