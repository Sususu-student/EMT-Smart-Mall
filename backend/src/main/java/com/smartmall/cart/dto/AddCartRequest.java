package com.smartmall.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCartRequest {

    @NotBlank(message = "商品 ID 不能为空")
    private String productId;

    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "单次最多购买 99 件")
    private Integer quantity = 1;
}
