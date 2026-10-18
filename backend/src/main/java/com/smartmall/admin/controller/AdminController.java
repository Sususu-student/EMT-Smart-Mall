package com.smartmall.admin.controller;

import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.admin.dto.DashboardVO;
import com.smartmall.admin.service.AdminService;
import com.smartmall.agent.dto.ExecutionLogVO;
import com.smartmall.agent.service.AgentLogService;
import com.smartmall.auth.AdminOnly;
import com.smartmall.common.ApiResponse;
import com.smartmall.common.PageResult;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.user.dto.UserVO;
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

@AdminOnly
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final AgentLogService agentLogService;

    @GetMapping("/stats")
    public ApiResponse<DashboardVO> stats() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductVO>> products(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String categoryId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.products(keyword, categoryId, status, page, size));
    }

    @PostMapping("/product")
    public ApiResponse<ProductVO> createProduct(@Valid @RequestBody AdminProductRequest request) {
        return ApiResponse.ok(adminService.createProduct(request));
    }

    @PostMapping("/products/import")
    public ApiResponse<java.util.Map<String, Object>> importProducts(
            @RequestBody java.util.List<@jakarta.validation.Valid AdminProductRequest> requests) {
        return ApiResponse.ok(adminService.importProducts(requests));
    }

    @PutMapping("/product/{id}")
    public ApiResponse<ProductVO> updateProduct(@PathVariable String id,
                                                @Valid @RequestBody AdminProductRequest request) {
        return ApiResponse.ok(adminService.updateProduct(id, request));
    }

    @DeleteMapping("/product/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable String id) {
        adminService.deleteProduct(id);
        return ApiResponse.ok();
    }

    @PutMapping("/product/{id}/status")
    public ApiResponse<Void> updateProductStatus(@PathVariable String id, @RequestParam Integer status) {
        adminService.updateProductStatus(id, status);
        return ApiResponse.ok();
    }

    @GetMapping("/orders")
    public ApiResponse<PageResult<OrderVO>> orders(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(orderService.adminList(keyword, status, page, size));
    }

    @PutMapping("/order/{id}/ship")
    public ApiResponse<OrderVO> ship(@PathVariable String id) {
        return ApiResponse.ok(orderService.adminShip(id));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserVO>> users(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.users(keyword, page, size));
    }

    @PutMapping("/user/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable String id, @RequestParam Integer status) {
        adminService.updateUserStatus(id, status);
        return ApiResponse.ok();
    }

    @GetMapping("/agent-logs")
    public ApiResponse<PageResult<ExecutionLogVO>> agentLogs(@RequestParam(required = false) String taskId,
                                                             @RequestParam(required = false) String conversationId,
                                                             @RequestParam(required = false) String agentId,
                                                             @RequestParam(required = false) String step,
                                                             @RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(agentLogService.search(taskId, conversationId, agentId, step, page, size));
    }

    @GetMapping("/agent-steps")
    public ApiResponse<List<ExecutionLogVO>> agentSteps(@RequestParam String taskId) {
        return ApiResponse.ok(agentLogService.byTask(taskId));
    }
}
