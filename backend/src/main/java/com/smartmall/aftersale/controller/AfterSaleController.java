package com.smartmall.aftersale.controller;

import com.smartmall.aftersale.dto.AfterSaleVO;
import com.smartmall.aftersale.dto.CreateAfterSaleRequest;
import com.smartmall.aftersale.service.AfterSaleService;
import com.smartmall.common.ApiResponse;
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
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @PostMapping
    public ApiResponse<AfterSaleVO> create(@Valid @RequestBody CreateAfterSaleRequest request) {
        return ApiResponse.ok(afterSaleService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AfterSaleVO>> mine() {
        return ApiResponse.ok(afterSaleService.mine());
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<List<AfterSaleVO>> byOrder(@PathVariable String orderId) {
        return ApiResponse.ok(afterSaleService.byOrder(orderId));
    }
}
