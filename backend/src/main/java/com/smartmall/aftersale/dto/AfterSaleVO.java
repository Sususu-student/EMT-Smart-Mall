package com.smartmall.aftersale.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AfterSaleVO {

    private String id;
    private String orderId;
    private String orderNo;
    private String userId;
    private String username;
    private String type;
    private String typeLabel;
    private String reason;
    private String status;
    private String statusLabel;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private java.math.BigDecimal orderAmount;
}
