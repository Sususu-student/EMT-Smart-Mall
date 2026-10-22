package com.smartmall.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AgentToolVO {

    private String name;
    private String description;
    private List<String> parameters;
    private String usedBy;
    private String type;
}
