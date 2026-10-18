package com.smartmall.agent.controller;

import com.smartmall.agent.dto.ChatRequest;
import com.smartmall.agent.dto.ChatResponse;
import com.smartmall.agent.dto.CompareRequest;
import com.smartmall.agent.dto.CompareResponse;
import com.smartmall.agent.dto.ConversationVO;
import com.smartmall.agent.dto.ExecutionLogVO;
import com.smartmall.agent.dto.TurnVO;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.agent.service.AgentLogService;
import com.smartmall.agent.service.CompareAgentService;
import com.smartmall.agent.service.ConversationService;
import com.smartmall.agent.service.GuideAgentService;
import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.auth.PublicApi;
import com.smartmall.common.ApiResponse;
import com.smartmall.common.Ids;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final GuideAgentService guideAgentService;
    private final CompareAgentService compareAgentService;
    private final ConversationService conversationService;
    private final AgentLogService agentLogService;
    private final LlmService llmService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(guideAgentService.chat(request));
    }

    @PublicApi
    @PostMapping("/compare")
    public ApiResponse<CompareResponse> compare(@RequestBody CompareRequest request) {
        CurrentUser current = AuthContext.get();
        String userId = current == null ? null : current.id();
        if (request.getConversationId() != null && userId != null) {
            conversationService.requireOwned(request.getConversationId(), userId);
        }
        return ApiResponse.ok(compareAgentService.compare(request, userId, Ids.next(), request.getConversationId()));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> conversations() {
        return ApiResponse.ok(conversationService.list(AuthContext.userId()));
    }

    @GetMapping("/conversations/{id}/turns")
    public ApiResponse<List<TurnVO>> turns(@PathVariable String id) {
        return ApiResponse.ok(conversationService.turns(id, AuthContext.userId()));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable String id) {
        conversationService.delete(id, AuthContext.userId());
        return ApiResponse.ok();
    }

    @GetMapping("/logs")
    public ApiResponse<List<ExecutionLogVO>> logs(@RequestParam(required = false) String taskId,
                                                  @RequestParam(required = false) String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            conversationService.requireOwned(conversationId, AuthContext.userId());
            return ApiResponse.ok(agentLogService.byConversation(conversationId));
        }
        if (taskId != null && !taskId.isBlank()) {
            List<ExecutionLogVO> logs = agentLogService.byTask(taskId);
            if (!logs.isEmpty() && logs.get(0).getConversationId() != null) {
                conversationService.requireOwned(logs.get(0).getConversationId(), AuthContext.userId());
            }
            return ApiResponse.ok(logs);
        }
        throw new com.smartmall.common.BizException("请提供 taskId 或 conversationId");
    }

    @PublicApi
    @GetMapping("/status")
    public ApiResponse<LlmService.LlmStatus> status() {
        return ApiResponse.ok(llmService.status());
    }
}
