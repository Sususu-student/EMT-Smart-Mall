package com.smartmall.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    private String type;
    private String taskId;
    private String conversationId;
    private String step;
    private String stepName;
    private String status;
    private Long durationMs;
    private String summary;
    private LocalDateTime timestamp;

    public static AgentEvent step(String taskId, String conversationId, AgentStep step) {
        return new AgentEvent("step", taskId, conversationId, step.getStep(), step.getStepName(),
                step.getStatus(), step.getDurationMs(), step.getSummary(), LocalDateTime.now());
    }

    public static AgentEvent done(String taskId, String conversationId, String summary) {
        return new AgentEvent("done", taskId, conversationId, "done", "执行完成",
                "success", 0L, summary, LocalDateTime.now());
    }
}
