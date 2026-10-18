package com.smartmall.cart.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVO {

    private Long id;
    private String productId;
    private String name;
    private String image;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer quantity;
    private Integer selected;
    private BigDecimal subtotal;
    private Boolean available;
}
