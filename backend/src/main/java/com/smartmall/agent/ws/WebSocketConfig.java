package com.smartmall.agent.ws;

import com.smartmall.customer.ws.CsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final CsWebSocketHandler csWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOriginPatterns("*");
        // 客服实时通道：与 AI 导购分开，避免两套消息语义互相干扰
        registry.addHandler(csWebSocketHandler, "/ws/cs")
                .setAllowedOriginPatterns("*");
    }
}
