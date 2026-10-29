package com.smartmall.customer.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmall.auth.CurrentUser;
import com.smartmall.auth.JwtService;
import com.smartmall.common.JsonUtils;
import com.smartmall.customer.entity.CsConversation;
import com.smartmall.customer.entity.CsMessage;
import com.smartmall.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 客服实时通道：买家与卖家在同一个会话房间内即时收发消息。
 *
 * 与 AI 导购的 /ws/agent 分开，避免两套消息语义互相干扰：
 *   - agent 通道推送的是执行事件（思考步骤、流式内容）
 *   - 这里推送的是即时消息（谁说了什么）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final CustomerService customerService;

    /** conversationId -> 在线连接集合 */
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = queryParam(session, "token");
        if (token == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            CurrentUser user = jwtService.parse(token);
            session.getAttributes().put("userId", user.id());
            session.getAttributes().put("role", user.role());
        } catch (Exception e) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        String conversationId = queryParam(session, "conversationId");
        if (conversationId != null) {
            join(session, conversationId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = JsonUtils.mapper().readTree(message.getPayload());
            String action = node.path("action").asText("");
            String conversationId = node.path("conversationId").asText("");
            String userId = (String) session.getAttributes().get("userId");
            String role = (String) session.getAttributes().get("role");
            if (conversationId.isBlank() || userId == null) {
                return;
            }
            switch (action) {
                case "join" -> join(session, conversationId);
                case "leave" -> leave(session, conversationId);
                case "send" -> {
                    String content = node.path("content").asText("");
                    CsMessage saved = customerService.send(conversationId, userId, role, content);
                    push(conversationId, saved);
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("客服消息处理失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        rooms.values().forEach(set -> set.remove(session));
    }

    /** 加入会话房间，需要校验归属，防止串台 */
    private void join(WebSocketSession session, String conversationId) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        try {
            CsConversation conv = customerService.get(conversationId, userId);
            if (conv == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        rooms.computeIfAbsent(conversationId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    private void leave(WebSocketSession session, String conversationId) {
        Set<WebSocketSession> set = rooms.get(conversationId);
        if (set != null) {
            set.remove(session);
        }
    }

    /** 把消息推给房间内所有在线连接（包括发送方，便于确认送达） */
    private void push(String conversationId, CsMessage msg) {
        Set<WebSocketSession> set = rooms.get(conversationId);
        if (set == null || set.isEmpty()) {
            return;
        }
        ObjectNode payload = JsonUtils.mapper().createObjectNode();
        payload.put("type", "message");
        payload.put("conversationId", conversationId);
        payload.put("id", msg.getId());
        payload.put("senderId", msg.getSenderId());
        payload.put("senderRole", msg.getSenderRole());
        payload.put("content", msg.getContent());
        payload.put("createdAt", msg.getCreatedAt() == null ? "" : msg.getCreatedAt().toString());
        TextMessage text = new TextMessage(payload.toString());
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(text);
                } catch (Exception e) {
                    log.debug("客服推送失败: {}", e.getMessage());
                }
            }
        }
    }

    private String queryParam(WebSocketSession session, String name) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && pair.substring(0, i).equals(name)) {
                try {
                    return java.net.URLDecoder.decode(pair.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}