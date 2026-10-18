package com.smartmall.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentStep {

    private String step;
    private String stepName;
    private String status;
    private long durationMs;
    private String summary;
    private String createdAt;
}
