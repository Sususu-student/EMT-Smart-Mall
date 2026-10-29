package com.smartmall.customer.dto;

import com.smartmall.customer.entity.CsMessage;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服消息 */
@Data
public class CsMessageVO {

    private Long id;
    private String conversationId;
    private String senderId;
    private String senderRole;
    private String content;
    private LocalDateTime createdAt;

    public static CsMessageVO of(CsMessage m) {
        CsMessageVO vo = new CsMessageVO();
        vo.setId(m.getId());
        vo.setConversationId(m.getConversationId());
        vo.setSenderId(m.getSenderId());
        vo.setSenderRole(m.getSenderRole());
        vo.setContent(m.getContent());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
