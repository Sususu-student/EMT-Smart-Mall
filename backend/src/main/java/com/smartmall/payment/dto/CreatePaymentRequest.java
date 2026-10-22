package com.smartmall.payment.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreatePaymentRequest {

    @Pattern(regexp = "ALIPAY|WECHAT|CARD|MOCK", message = "不支持的支付渠道")
    private String channel = "ALIPAY";
}
