package com.smartmall.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutionLogVO {

    private Long id;
    private String taskId;
    private String conversationId;
    private String agentId;
    private String step;
    private String stepName;
    private String input;
    private String output;
    private String status;
    private Integer durationMs;
    private LocalDateTime createdAt;
}
