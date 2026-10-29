package com.smartmall.analytics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 卖家数据分析：KPI + 销售趋势 + 商品散点 + 评分分布 + 履约漏斗。
 */
@Data
public class SellerAnalyticsVO {

    /** 核心指标卡片 */
    private Kpi kpi;
    /** 销售趋势（支持 7/30/90 天） */
    private List<TrendPoint> trend;
    /** 商品表现散点：浏览量 vs 销量 */
    private List<ProductPoint> products;
    /** 评分分布 5→1 星 */
    private List<RatingBucket> ratings;
    /** 好评/差评高频词 */
    private List<Keyword> reviewKeywords;
    /** 履约漏斗 */
    private List<FunnelStep> funnel;

    @Data
    public static class Kpi {
        private BigDecimal totalSales = BigDecimal.ZERO;
        private long orderCount;
        private long pendingShipCount;
        private long viewCount;
        private BigDecimal avgRating = BigDecimal.ZERO;
        private long productCount;
    }

    @Data
    public static class TrendPoint {
        /** 日期 yyyy-MM-dd */
        private String date;
        private BigDecimal sales;
        /** 客单价 */
        private BigDecimal avgOrder;
        private long orders;
    }

    @Data
    public static class ProductPoint {
        private String name;
        private long views;
        private long sales;
        private int stock;
        private BigDecimal rating;
    }

    @Data
    public static class RatingBucket {
        private int star;
        private long count;
    }

    @Data
    public static class Keyword {
        private String word;
        private int weight;
        /** good 好评词 / bad 差评词 */
        private String type;
    }

    @Data
    public static class FunnelStep {
        private String name;
        private long value;
        /** 相对上一步的转化率，第一层为 100 */
        private BigDecimal rate;
    }
}