package com.smartmall.product.controller;

import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.auth.PublicApi;
import com.smartmall.common.ApiResponse;
import com.smartmall.common.PageResult;
import com.smartmall.product.dto.CategoryVO;
import com.smartmall.product.dto.ProductQuery;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.dto.ReviewRequest;
import com.smartmall.product.entity.ProductReview;
import com.smartmall.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PublicApi
    @GetMapping("/list")
    public ApiResponse<PageResult<ProductVO>> list(@ModelAttribute ProductQuery query) {
        return ApiResponse.ok(productService.list(query, optionalUserId()));
    }

    @PublicApi
    @GetMapping("/search")
    public ApiResponse<PageResult<ProductVO>> search(@ModelAttribute ProductQuery query) {
        return ApiResponse.ok(productService.list(query, optionalUserId()));
    }

    @PublicApi
    @GetMapping("/category")
    public ApiResponse<List<CategoryVO>> categories() {
        return ApiResponse.ok(productService.categoryTree());
    }

    @PublicApi
    @GetMapping("/{id}")
    public ApiResponse<ProductVO> detail(@PathVariable String id) {
        return ApiResponse.ok(productService.detail(id, optionalUserId()));
    }

    @PublicApi
    @GetMapping("/batch")
    public ApiResponse<List<ProductVO>> batch(@RequestParam String ids) {
        List<String> idList = java.util.Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).limit(8).toList();
        return ApiResponse.ok(productService.byIds(idList));
    }

    @PublicApi
    @GetMapping("/{id}/related")
    public ApiResponse<List<ProductVO>> related(@PathVariable String id) {
        return ApiResponse.ok(productService.related(id, 4));
    }

    @PublicApi
    @GetMapping("/{id}/reviews")
    public ApiResponse<PageResult<ProductReview>> reviews(@PathVariable String id,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(productService.reviews(id, page, size));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<ProductReview> addReview(@PathVariable String id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(productService.addReview(id, request));
    }

    private String optionalUserId() {
        CurrentUser user = AuthContext.get();
        return user == null ? null : user.id();
    }
}
