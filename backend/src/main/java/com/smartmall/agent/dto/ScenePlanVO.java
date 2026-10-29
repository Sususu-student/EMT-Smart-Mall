package com.smartmall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 场景搭配方案：前端渲染成一张图文卡片。
 */
@Data
public class ScenePlanVO {

    private String sceneKey;
    private String sceneName;
    private BigDecimal budget;
    private BigDecimal total;
    private boolean withinBudget;
    /** 由 LLM 生成的搭配理由 */
    private String reason;
    private List<PlanItemVO> items;

    @Data
    public static class PlanItemVO {
        private String productId;
        private String name;
        private String image;
        private String imageFallback;
        private BigDecimal price;
        /** 该商品在方案中承担的角色，如「防晒护肤」 */
        private String role;
        private BigDecimal rating;
    }
}