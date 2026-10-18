package com.smartmall.agent.dto;

import com.smartmall.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class ScoredProduct {

    private Product product;
    private double score;
    private List<String> highlights = new ArrayList<>();

    public ScoredProduct(Product product, double score) {
        this.product = product;
        this.score = score;
    }
}
