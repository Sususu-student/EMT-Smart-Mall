package com.smartmall.user.controller;

import com.smartmall.auth.PublicApi;
import com.smartmall.common.ApiResponse;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.user.dto.AddressRequest;
import com.smartmall.user.dto.ChangePasswordRequest;
import com.smartmall.user.dto.LoginRequest;
import com.smartmall.user.dto.LoginResponse;
import com.smartmall.user.dto.ResetPasswordRequest;
import com.smartmall.user.dto.PreferenceUpdateRequest;
import com.smartmall.user.dto.PreferenceVO;
import com.smartmall.user.dto.ProfileVO;
import com.smartmall.user.dto.RegisterRequest;
import com.smartmall.user.dto.UpdateProfileRequest;
import com.smartmall.user.dto.UserVO;
import com.smartmall.user.entity.UserAddress;
import com.smartmall.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PublicApi
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    @PublicApi
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    /** 自助找回密码：用户名 + 注册邮箱校验通过后设置新密码 */
    @PublicApi
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ApiResponse.ok();
    }

    /**
     * 静默续期：用即将过期或刚过期的 token 换一个新 token。
     * 标 @PublicApi 是因为此时 token 可能已过期，拦截器会直接拒绝，
     * 由 service 内部用宽松模式校验（签名有效 + 过期不超过 7 天）。
     */
    @PublicApi
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String header) {
        String token = body != null ? body.get("token") : null;
        if (!StringUtils.hasText(token) && header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        }
        return ApiResponse.ok(userService.refresh(token));
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileVO> profile() {
        return ApiResponse.ok(userService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.ok();
    }

    @GetMapping("/address")
    public ApiResponse<List<UserAddress>> addresses() {
        return ApiResponse.ok(userService.addresses());
    }

    @PostMapping("/address")
    public ApiResponse<UserAddress> createAddress(@Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(userService.createAddress(request));
    }

    @PutMapping("/address/{id}")
    public ApiResponse<UserAddress> updateAddress(@PathVariable String id,
                                                  @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(userService.updateAddress(id, request));
    }

    @DeleteMapping("/address/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable String id) {
        userService.deleteAddress(id);
        return ApiResponse.ok();
    }

    @PutMapping("/address/{id}/default")
    public ApiResponse<UserAddress> setDefaultAddress(@PathVariable String id) {
        return ApiResponse.ok(userService.setDefaultAddress(id));
    }

    @GetMapping("/preference")
    public ApiResponse<List<PreferenceVO>> preferences() {
        return ApiResponse.ok(userService.preferences());
    }

    @PutMapping("/preference")
    public ApiResponse<List<PreferenceVO>> replacePreferences(@Valid @RequestBody PreferenceUpdateRequest request) {
        return ApiResponse.ok(userService.replacePreferences(request));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<ProductVO>> favorites() {
        return ApiResponse.ok(userService.favorites());
    }

    @GetMapping("/favorite/{productId}/check")
    public ApiResponse<Boolean> checkFavorite(@PathVariable String productId) {
        return ApiResponse.ok(userService.isFavorite(productId));
    }

    @PostMapping("/favorite/{productId}")
    public ApiResponse<Boolean> toggleFavorite(@PathVariable String productId) {
        return ApiResponse.ok(userService.toggleFavorite(productId));
    }

    @GetMapping("/history")
    public ApiResponse<List<ProductVO>> history(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.ok(userService.browseHistory(limit));
    }
}
