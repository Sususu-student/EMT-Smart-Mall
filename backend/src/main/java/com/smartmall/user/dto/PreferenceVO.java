package com.smartmall.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceVO {

    private String key;
    private String value;
    private java.math.BigDecimal weight;
}
