package com.smartmall.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AdminProductRequest {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String subtitle;

    @NotBlank(message = "品类不能为空")
    private String categoryId;

    private String brand;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    private BigDecimal originalPrice;

    @Min(value = 0, message = "库存不能为负")
    private Integer stock = 0;

    private String description;

    private Map<String, Object> attributes = new LinkedHashMap<>();

    private String tags;

    private String image;

    private Integer status = 1;
}
