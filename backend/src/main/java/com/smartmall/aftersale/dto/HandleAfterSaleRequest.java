package com.smartmall.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HandleAfterSaleRequest {

    @NotBlank(message = "处理结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED", message = "处理结果只能为 APPROVED 或 REJECTED")
    private String status;

    @Size(max = 300, message = "处理备注最多 300 字")
    private String adminNote;
}
