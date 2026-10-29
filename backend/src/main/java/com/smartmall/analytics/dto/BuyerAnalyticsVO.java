package com.smartmall.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 买家消费画像：品类环形图 + 偏好雷达图 + AI 关键词云。
 */
@Data
public class BuyerAnalyticsVO {

    /** 累计消费金额（仅统计已完成/已发货/已支付订单） */
    private BigDecimal totalSpent = BigDecimal.ZERO;
    /** 参与统计的订单数 */
    private long orderCount;
    /** 累计购买件数 */
    private long itemCount;
    /** 品类消费构成（环形图） */
    private List<CategoryShare> categoryShares;
    /** 六维偏好得分（雷达图） */
    private List<PreferenceScore> preferences;
    /** AI 对话高频词（词云） */
    private List<Keyword> keywords;

    @Data
    public static class CategoryShare {
        private String name;
        private BigDecimal amount;
        private long count;
    }

    @Data
    public static class PreferenceScore {
        private String name;
        /** 归一化后的 0-100 得分 */
        private int score;
        /** 原始加权值，便于悬停展示 */
        private long raw;
    }

    @Data
    public static class Keyword {
        private String word;
        /** 出现次数，决定字号 */
        private int weight;
        /** 分类：category 品类 / feature 属性 / scene 场景 / price 价格 */
        private String type;
    }
}