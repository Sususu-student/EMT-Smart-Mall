package com.smartmall.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateQuantityRequest {

    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "单件商品最多 99 件")
    private Integer quantity;
}
