package com.smartmall.cart.controller;

import com.smartmall.cart.dto.AddCartRequest;
import com.smartmall.cart.dto.CartItemVO;
import com.smartmall.cart.dto.CartSelectRequest;
import com.smartmall.cart.dto.CartSummaryVO;
import com.smartmall.cart.dto.UpdateQuantityRequest;
import com.smartmall.cart.service.CartService;
import com.smartmall.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartSummaryVO> summary() {
        return ApiResponse.ok(cartService.summary());
    }

    @PostMapping
    public ApiResponse<CartItemVO> add(@Valid @RequestBody AddCartRequest request) {
        return ApiResponse.ok(cartService.add(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CartItemVO> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateQuantityRequest request) {
        return ApiResponse.ok(cartService.updateQuantity(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cartService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/select")
    public ApiResponse<Void> select(@RequestBody CartSelectRequest request) {
        cartService.select(request);
        return ApiResponse.ok();
    }
}
