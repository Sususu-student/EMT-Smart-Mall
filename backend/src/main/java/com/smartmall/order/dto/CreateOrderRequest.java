package com.smartmall.order.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    /** 立即购买时传入；购物车结算时留空 */
    private String productId;

    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity = 1;

    private List<Long> cartItemIds;

    private String addressId;

    private String remark;
}
