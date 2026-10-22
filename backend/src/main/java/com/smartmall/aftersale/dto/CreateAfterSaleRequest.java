package com.smartmall.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAfterSaleRequest {

    @NotBlank(message = "订单 ID 不能为空")
    private String orderId;

    @NotBlank(message = "售后类型不能为空")
    @Pattern(regexp = "REFUND|RETURN|EXCHANGE", message = "售后类型只能为 REFUND、RETURN 或 EXCHANGE")
    private String type;

    @NotBlank(message = "售后原因不能为空")
    @Size(max = 500, message = "售后原因最多 500 字")
    private String reason;
}
