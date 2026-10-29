package com.smartmall.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.common.BizException;
import com.smartmall.customer.entity.CsConversation;
import com.smartmall.customer.entity.CsMessage;
import com.smartmall.customer.mapper.CsConversationMapper;
import com.smartmall.customer.mapper.CsMessageMapper;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 客服会话：买家就某笔订单联系卖家。
 *
 * 设计取舍：
 *   - 会话按「订单 + 买家 + 卖家」唯一绑定，避免买家对同一单开多个窗口；
 *   - 消息落库，未读数在会话表上冗余，列表页无需聚合查询；
 *   - 实时推送由 CsWebSocketHandler 负责，这里只负责持久化与查询。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CsConversationMapper conversationMapper;
    private final CsMessageMapper messageMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    /** 买家从订单详情发起：找到或创建会话 */
    @Transactional
    public CsConversation openByOrder(String orderId, String buyerId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!buyerId.equals(order.getUserId())) {
            throw new BizException(403, "只能联系自己订单的卖家");
        }
        // 从订单明细里找出卖家（一单可能含多个卖家，取第一个）
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        String sellerId = items.stream()
                .map(OrderItem::getSellerId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (sellerId == null) {
            throw new BizException("该订单不含卖家商品，无需联系卖家");
        }

        CsConversation existing = conversationMapper.selectOne(new LambdaQueryWrapper<CsConversation>()
                .eq(CsConversation::getOrderId, orderId)
                .eq(CsConversation::getBuyerId, buyerId)
                .eq(CsConversation::getSellerId, sellerId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        CsConversation conv = new CsConversation();
        conv.setId(UUID.randomUUID().toString().replace("-", ""));
        conv.setOrderId(orderId);
        conv.setBuyerId(buyerId);
        conv.setSellerId(sellerId);
        conv.setBuyerUnread(0);
        conv.setSellerUnread(0);
        conv.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    /** 会话详情，校验当前用户属于该会话 */
    public CsConversation get(String conversationId, String userId) {
        CsConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException("会话不存在");
        }
        if (!userId.equals(conv.getBuyerId()) && !userId.equals(conv.getSellerId())) {
            throw new BizException(403, "无权访问该会话");
        }
        return conv;
    }

    /** 某会话的消息列表（按时间正序） */
    public List<CsMessage> messages(String conversationId, String userId) {
        get(conversationId, userId);
        return messageMapper.selectList(new LambdaQueryWrapper<CsMessage>()
                .eq(CsMessage::getConversationId, conversationId)
                .orderByAsc(CsMessage::getId)
                .last("LIMIT 200"));
    }

    /**
     * 发送消息：写入消息表并更新会话摘要与对方未读数。
     * 返回落库后的消息，供 WebSocket 推送给双方。
     */
    @Transactional
    public CsMessage send(String conversationId, String senderId, String senderRole, String content) {
        if (!StringUtils.hasText(content)) {
            throw new BizException("消息内容不能为空");
        }
        if (content.length() > 1000) {
            throw new BizException("消息过长，请控制在 1000 字以内");
        }
        CsConversation conv = get(conversationId, senderId);

        CsMessage msg = new CsMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setContent(content.trim());
        msg.setReadFlag(0);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        conv.setLastMessage(content.length() > 80 ? content.substring(0, 80) : content);
        conv.setLastAt(msg.getCreatedAt());
        boolean fromBuyer = senderId.equals(conv.getBuyerId());
        if (fromBuyer) {
            conv.setSellerUnread((conv.getSellerUnread() == null ? 0 : conv.getSellerUnread()) + 1);
        } else {
            conv.setBuyerUnread((conv.getBuyerUnread() == null ? 0 : conv.getBuyerUnread()) + 1);
        }
        conversationMapper.updateById(conv);
        return msg;
    }

    /** 进入会话时清除自己的未读数 */
    public void markRead(String conversationId, String userId) {
        CsConversation conv = get(conversationId, userId);
        if (userId.equals(conv.getBuyerId())) {
            conv.setBuyerUnread(0);
        } else {
            conv.setSellerUnread(0);
        }
        conversationMapper.updateById(conv);
        // 顺便把对方发来的消息标记为已读
        List<CsMessage> unread = messageMapper.selectList(new LambdaQueryWrapper<CsMessage>()
                .eq(CsMessage::getConversationId, conversationId)
                .ne(CsMessage::getSenderId, userId)
                .eq(CsMessage::getReadFlag, 0));
        for (CsMessage m : unread) {
            m.setReadFlag(1);
            messageMapper.updateById(m);
        }
    }

    /** 我的会话列表（买家视角或卖家视角由 userId 自动判断） */
    public List<CsConversation> listMine(String userId) {
        List<CsConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<CsConversation>()
                .and(w -> w.eq(CsConversation::getBuyerId, userId).or().eq(CsConversation::getSellerId, userId))
                .orderByDesc(CsConversation::getLastAt)
                .last("LIMIT 50"));
        return list == null ? new ArrayList<>() : list;
    }

    /** 卖家侧的未读消息总数，用于侧栏角标 */
    public long sellerUnreadTotal(String sellerId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<CsConversation>()
                        .eq(CsConversation::getSellerId, sellerId))
                .stream()
                .mapToLong(c -> c.getSellerUnread() == null ? 0 : c.getSellerUnread())
                .sum();
    }
}