package com.smartmall.cart.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartSelectRequest {

    private List<Long> ids;
    private Boolean selected = true;
}
