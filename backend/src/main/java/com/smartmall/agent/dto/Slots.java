package com.smartmall.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class Slots {

    private String categoryId;
    private String categoryName;
    private String keyword;
    private String brand;
    private String scene;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> features = new ArrayList<>();
    private boolean budgetUnlimited;
    private boolean askedBudget;
    private String pending;
    private List<String> lastProductIds = new ArrayList<>();
    private List<String> lastProductNames = new ArrayList<>();

    public boolean hasCategory() {
        return categoryId != null && !categoryId.isBlank();
    }

    public boolean hasBudget() {
        return maxPrice != null || minPrice != null || budgetUnlimited;
    }

    public void rememberProducts(List<String> ids, List<String> names) {
        this.lastProductIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
        this.lastProductNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
    }
}
