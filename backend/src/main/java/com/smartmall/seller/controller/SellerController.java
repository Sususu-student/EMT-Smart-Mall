package com.smartmall.seller.controller;

import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.common.ApiResponse;
import com.smartmall.common.PageResult;
import com.smartmall.order.dto.ShipRequest;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 卖家中心接口。所有写操作都会校验商品归属，卖家只能管理自己的商品。
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(sellerService.overview());
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductVO>> products(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(sellerService.myProducts(page, size, keyword, status));
    }

    @PostMapping("/products")
    public ApiResponse<ProductVO> publish(@Valid @RequestBody AdminProductRequest request) {
        return ApiResponse.ok(sellerService.publish(request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductVO> update(@PathVariable String id,
                                         @Valid @RequestBody AdminProductRequest request) {
        return ApiResponse.ok(sellerService.update(id, request));
    }

    @PutMapping("/products/{id}/status")
    public ApiResponse<ProductVO> toggleStatus(@PathVariable String id,
                                               @RequestParam Integer status) {
        return ApiResponse.ok(sellerService.toggleStatus(id, status));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        sellerService.delete(id);
        return ApiResponse.ok();
    }

    /** 订单列表：按子项发货状态筛选（PENDING / SHIPPED / 空为全部） */
    @GetMapping("/orders")
    public ApiResponse<PageResult<Map<String, Object>>> orders(
            @RequestParam(required = false) String shipStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(sellerService.orders(shipStatus, page, size));
    }

    /** 订单详情：含自己商品的明细与收货信息 */
    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable String id) {
        return ApiResponse.ok(sellerService.orderDetail(id));
    }

    /** 发货：只更新属于自己且待发货的子项 */
    @PostMapping("/orders/{id}/ship")
    public ApiResponse<Map<String, Object>> ship(@PathVariable String id,
                                                 @RequestBody(required = false) ShipRequest request) {
        return ApiResponse.ok(sellerService.ship(id, request));
    }
}