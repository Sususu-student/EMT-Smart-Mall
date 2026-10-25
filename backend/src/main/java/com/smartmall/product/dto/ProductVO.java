package com.smartmall.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ProductVO {

    private String id;
    private String name;
    private String subtitle;
    private String categoryId;
    private String categoryName;
    private String brand;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sales;
    private BigDecimal rating;
    private Integer reviewCount;
    private String description;
    private Map<String, Object> attributes;
    private List<Map<String, Object>> attributeSchema;
    private List<String> tags;
    private String image;
    private String imageFallback;
    private String sellerId;
    private String sellerName;
    private List<String> images;
    private Boolean favorited;
    private Integer status;
    private LocalDateTime createdAt;
}
