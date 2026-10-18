package com.smartmall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class CompareResponse {

    private List<CompareProduct> products = new ArrayList<>();
    private List<CompareDimension> dimensions = new ArrayList<>();
    private List<ProductScore> scores = new ArrayList<>();
    private String recommendedId;
    private String recommendedName;
    private String advice;
    private List<String> highlights = new ArrayList<>();
    private boolean degraded;
    private String taskId;

    @Data
    public static class CompareProduct {
        private String id;
        private String name;
        private String image;
        private BigDecimal price;
        private BigDecimal rating;
        private Integer stock;
        private String brand;
    }

    @Data
    public static class CompareDimension {
        private String key;
        private String unit;
        private String type;
        private boolean higherBetter;
        private double weight;
        private Map<String, String> displayValues = new LinkedHashMap<>();
        private Map<String, Double> normalizedValues = new LinkedHashMap<>();
        private String bestProductId;
    }

    @Data
    public static class ProductScore {
        private String productId;
        private double totalScore;
        private Map<String, Double> contributions = new LinkedHashMap<>();
        private int rank;
    }
}
