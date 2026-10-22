package com.smartmall.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentPromptVO {

    private String key;
    private String name;
    private String description;
    private String content;
    private boolean customized;
    private LocalDateTime updatedAt;
}
