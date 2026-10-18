package com.smartmall.agent.dto;

import com.smartmall.product.dto.ProductVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RecommendedProduct {

    private ProductVO product;
    private String reason;
    private double score;
    private List<String> highlights = new ArrayList<>();
    private boolean reasonFallback;
}
