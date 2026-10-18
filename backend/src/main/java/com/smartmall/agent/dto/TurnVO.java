package com.smartmall.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TurnVO {

    private Long id;
    private String role;
    private String content;
    private String intent;
    private List<RecommendedProduct> products;
    private String taskId;
    private LocalDateTime createdAt;
}
