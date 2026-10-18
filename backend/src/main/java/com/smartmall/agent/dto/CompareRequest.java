package com.smartmall.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompareRequest {

    private List<String> productIds;
    private java.util.Map<String, Double> weights;
    private Boolean usePreference = true;
    private String conversationId;
}
