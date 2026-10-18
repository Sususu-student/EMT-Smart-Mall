package com.smartmall.agent.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmall.agent.dto.AgentEvent;
import com.smartmall.agent.entity.Conversation;
import com.smartmall.agent.mapper.ConversationMapper;
import com.smartmall.auth.CurrentUser;
import com.smartmall.auth.JwtService;
import com.smartmall.common.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final ConversationMapper conversationMapper;
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParam(session, "token");
        if (token == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            CurrentUser user = jwtService.parse(token);
            session.getAttributes().put("userId", user.id());
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        String conversationId = queryParam(session, "conversationId");
        if (conversationId != null) {
            subscribe(session, conversationId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = JsonUtils.mapper().readTree(message.getPayload());
        String action = node.path("action").asText("");
        String conversationId = node.path("conversationId").asText("");
        if (conversationId.isBlank()) {
            return;
        }
        if ("subscribe".equals(action)) {
            subscribe(session, conversationId);
        } else if ("unsubscribe".equals(action)) {
            CopyOnWriteArraySet<WebSocketSession> set = rooms.get(conversationId);
            if (set != null) {
                set.remove(session);
            }
        }
    }

    private void subscribe(WebSocketSession session, String conversationId) {
        String userId = (String) session.getAttributes().get("userId");
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || userId == null || !userId.equals(conversation.getUserId())) {
            return;
        }
        rooms.computeIfAbsent(conversationId, key -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void broadcast(String conversationId, AgentEvent event) {
        if (conversationId == null) {
            return;
        }
        Set<WebSocketSession> sessions = rooms.get(conversationId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = JsonUtils.mapper().writeValueAsString(event);
        } catch (Exception e) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(payload));
                    }
                }
            } catch (Exception e) {
                log.debug("WebSocket 推送失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        rooms.values().forEach(set -> set.remove(session));
    }

    private String queryParam(WebSocketSession session, String name) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(name);
    }
}
