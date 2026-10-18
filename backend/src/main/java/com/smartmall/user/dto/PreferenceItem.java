package com.smartmall.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PreferenceItem {

    @NotBlank(message = "偏好键不能为空")
    private String key;

    @NotBlank(message = "偏好值不能为空")
    private String value;

    private BigDecimal weight = BigDecimal.ONE;
}
