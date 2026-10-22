package com.smartmall.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PaymentCallbackRequest {

    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;

    private Boolean success = true;
}
