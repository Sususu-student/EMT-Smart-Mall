package com.smartmall.cart.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartSummaryVO {

    private List<CartItemVO> items;
    private long totalQuantity;
    private int selectedCount;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal savedAmount;
}
