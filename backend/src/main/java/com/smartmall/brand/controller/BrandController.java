package com.smartmall.brand.controller;

import com.smartmall.auth.PublicApi;
import com.smartmall.brand.dto.BrandVO;
import com.smartmall.brand.service.BrandService;
import com.smartmall.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品牌档案接口，供首页品牌条与品牌弹层使用，游客可访问。
 */
@PublicApi
@RestController
@RequestMapping("/api/brand")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping("/list")
    public ApiResponse<List<BrandVO>> list() {
        return ApiResponse.ok(brandService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandVO> detail(@PathVariable String id) {
        return ApiResponse.ok(brandService.detail(id));
    }

    /** 按品牌名查档案，商品详情页用来判断是否展示品牌入口 */
    @GetMapping("/by-name")
    public ApiResponse<BrandVO> byName(@RequestParam String name) {
        return ApiResponse.ok(brandService.byName(name));
    }
}