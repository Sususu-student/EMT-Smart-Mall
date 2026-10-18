package com.smartmall.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogSeed {

    private List<SeedCategory> categories;
    private List<SeedProduct> products;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeedCategory {
        private String id;
        private String parentId;
        private String name;
        private String icon;
        private String description;
        private Integer sortOrder;
        private Object attributeSchema;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeedProduct {
        private String id;
        private String name;
        private String subtitle;
        private String categoryId;
        private String brand;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private Integer stock;
        private Integer sales;
        private BigDecimal rating;
        private Integer reviewCount;
        private String description;
        private Map<String, Object> attributes;
        private String tags;
        private String image;
        private Integer status;
    }
}
