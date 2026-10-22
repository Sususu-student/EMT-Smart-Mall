package com.smartmall.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String paymentNo;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String channel;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
