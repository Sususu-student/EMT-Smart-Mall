package com.smartmall.customer.dto;

import com.smartmall.customer.entity.CsConversation;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服会话，按当前用户视角返回未读数 */
@Data
public class CsConversationVO {

    private String id;
    private String orderId;
    private String buyerId;
    private String sellerId;
    private String lastMessage;
    private LocalDateTime lastAt;
    /** 当前用户的未读数 */
    private int unread;
    /** 当前用户在会话中的角色 */
    private String myRole;
    private LocalDateTime createdAt;

    public static CsConversationVO of(CsConversation c, String currentUserId) {
        CsConversationVO vo = new CsConversationVO();
        vo.setId(c.getId());
        vo.setOrderId(c.getOrderId());
        vo.setBuyerId(c.getBuyerId());
        vo.setSellerId(c.getSellerId());
        vo.setLastMessage(c.getLastMessage());
        vo.setLastAt(c.getLastAt());
        vo.setCreatedAt(c.getCreatedAt());
        boolean isBuyer = currentUserId.equals(c.getBuyerId());
        vo.setMyRole(isBuyer ? "buyer" : "seller");
        vo.setUnread(isBuyer
                ? (c.getBuyerUnread() == null ? 0 : c.getBuyerUnread())
                : (c.getSellerUnread() == null ? 0 : c.getSellerUnread()));
        return vo;
    }
}
