package com.smartmall.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服会话：买家就某笔订单与卖家的一对一会话 */
@Data
@TableName("cs_conversation")
public class CsConversation {

    private String id;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private String lastMessage;
    private LocalDateTime lastAt;
    private Integer buyerUnread;
    private Integer sellerUnread;
    private LocalDateTime createdAt;
}
