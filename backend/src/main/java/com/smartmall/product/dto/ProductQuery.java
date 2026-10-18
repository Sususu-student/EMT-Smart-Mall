package com.smartmall.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductQuery {

    private long page = 1;
    private long size = 12;
    private String categoryId;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String brand;
    private String sort = "default";

    public void normalize() {
        page = Math.max(1, page);
        size = Math.min(60, Math.max(1, size));
    }
}
