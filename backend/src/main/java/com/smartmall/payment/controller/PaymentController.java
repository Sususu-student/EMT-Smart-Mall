package com.smartmall.payment.controller;

import com.smartmall.auth.PublicApi;
import com.smartmall.common.ApiResponse;
import com.smartmall.payment.dto.CreatePaymentRequest;
import com.smartmall.payment.dto.PaymentCallbackRequest;
import com.smartmall.payment.entity.PaymentRecord;
import com.smartmall.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentRecord> create(@PathVariable String orderId,
                                             @Valid @RequestBody(required = false) CreatePaymentRequest request) {
        return ApiResponse.ok(paymentService.create(orderId,
                request == null ? "ALIPAY" : request.getChannel()));
    }

    @PublicApi
    @PostMapping("/callback")
    public ApiResponse<PaymentRecord> callback(@Valid @RequestBody PaymentCallbackRequest request) {
        return ApiResponse.ok(paymentService.callback(request.getPaymentNo(), request.getSuccess()));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentRecord> get(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.get(paymentNo));
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<List<PaymentRecord>> byOrder(@PathVariable String orderId) {
        return ApiResponse.ok(paymentService.byOrder(orderId));
    }
}
