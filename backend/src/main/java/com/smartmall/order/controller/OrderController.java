package com.smartmall.order.controller;

import com.smartmall.common.ApiResponse;
import com.smartmall.common.PageResult;
import com.smartmall.order.dto.CreateOrderRequest;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<OrderVO>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(orderService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> detail(@PathVariable String id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    @PutMapping("/{id}/pay")
    public ApiResponse<OrderVO> pay(@PathVariable String id) {
        return ApiResponse.ok(orderService.pay(id));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<OrderVO> cancel(@PathVariable String id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<OrderVO> confirm(@PathVariable String id) {
        return ApiResponse.ok(orderService.confirmReceipt(id));
    }
}
