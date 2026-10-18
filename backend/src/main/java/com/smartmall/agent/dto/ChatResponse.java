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
    private List<AgentStep> steps = new ArrayList<>();
    private boolean degraded;
    private String memorySummary;
}
