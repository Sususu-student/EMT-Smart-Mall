package com.smartmall.pricewatch.controller;

import com.smartmall.common.ApiResponse;
import com.smartmall.pricewatch.service.PriceWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 降价提醒接口：商品详情的关注开关。
 */
@RestController
@RequestMapping("/api/price-watch")
@RequiredArgsConstructor
public class PriceWatchController {

    private final PriceWatchService priceWatchService;

    /** 加入或取消提醒，返回 { watching: true/false } */
    @PostMapping("/{productId}")
    public ApiResponse<Map<String, Boolean>> toggle(@PathVariable String productId) {
        boolean watching = priceWatchService.toggle(productId);
        return ApiResponse.ok(Map.of("watching", watching));
    }

    /** 查询是否已关注该商品 */
    @GetMapping("/{productId}")
    public ApiResponse<Map<String, Boolean>> status(@PathVariable String productId) {
        return ApiResponse.ok(Map.of("watching", priceWatchService.watching(productId)));
    }
}