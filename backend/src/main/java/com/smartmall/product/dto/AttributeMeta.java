package com.smartmall.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AttributeMeta {

    private String key;
    private String unit;
    private String type = "number";
    private Boolean higherBetter = true;
    private BigDecimal weight = BigDecimal.ONE;
    private List<String> options;
}
