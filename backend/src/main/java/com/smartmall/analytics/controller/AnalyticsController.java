package com.smartmall.analytics.controller;

import com.smartmall.analytics.dto.BuyerAnalyticsVO;
import com.smartmall.analytics.dto.SellerAnalyticsVO;
import com.smartmall.analytics.service.AnalyticsService;
import com.smartmall.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析接口：买家消费画像、卖家经营看板。
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** 买家消费画像：品类构成、偏好雷达、AI 关键词 */
    @GetMapping("/buyer")
    public ApiResponse<BuyerAnalyticsVO> buyer() {
        return ApiResponse.ok(analyticsService.buyerAnalytics());
    }

    /** 卖家经营看板，days 支持 7 / 30 / 90 */
    @GetMapping("/seller")
    public ApiResponse<SellerAnalyticsVO> seller(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(analyticsService.sellerAnalytics(days));
    }
}