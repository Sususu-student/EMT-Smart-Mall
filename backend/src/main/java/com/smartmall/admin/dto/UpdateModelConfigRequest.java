package com.smartmall.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateModelConfigRequest {

    @NotBlank(message = "模型名不能为空")
    @Size(max = 80, message = "模型名过长")
    private String model;

    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private Double temperature;
}
