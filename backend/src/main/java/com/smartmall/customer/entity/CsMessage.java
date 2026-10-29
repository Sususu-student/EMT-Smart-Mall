package com.smartmall.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服消息 */
@Data
@TableName("cs_message")
public class CsMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String senderId;
    private String senderRole;
    private String content;
    private Integer readFlag;
    private LocalDateTime createdAt;
}
