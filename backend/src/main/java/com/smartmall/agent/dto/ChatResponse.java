package com.smartmall.agent.dto;

import com.smartmall.order.dto.OrderVO;
import com.smartmall.product.dto.ProductVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatResponse {

    private String conversationId;
    private String taskId;
    private String intent;
    private String reply;
    private List<RecommendedProduct> products = new ArrayList<>();
    private List<String> clarifyOptions = new ArrayList<>();
    private List<OrderVO> orders = new ArrayList<>();
    private CompareResponse compare;
    /** 场景搭配方案（意图为 scene_plan 时返回） */
    private ScenePlanVO scenePlan;
    private List<AgentStep> steps = new ArrayList<>();
    private boolean degraded;
    private String memorySummary;
    private Slots slots;
    private java.util.List<String> appliedPreferences = new java.util.ArrayList<>();
}
