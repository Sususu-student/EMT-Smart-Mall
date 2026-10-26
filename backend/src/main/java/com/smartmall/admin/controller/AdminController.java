package com.smartmall.admin.controller;

import com.smartmall.admin.dto.AdminCategoryRequest;
import com.smartmall.admin.dto.UpdateAgentPromptRequest;
import com.smartmall.admin.dto.UpdateModelConfigRequest;
import com.smartmall.admin.dto.AdminConversationVO;
import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.admin.dto.ReviewAdminVO;
import com.smartmall.admin.dto.DashboardVO;
import com.smartmall.admin.service.AdminService;
import com.smartmall.aftersale.dto.AfterSaleVO;
import com.smartmall.aftersale.dto.HandleAfterSaleRequest;
import com.smartmall.aftersale.service.AfterSaleService;
import com.smartmall.agent.dto.ExecutionLogVO;
import com.smartmall.agent.dto.AgentPromptVO;
import com.smartmall.agent.dto.AgentToolVO;
import com.smartmall.agent.dto.TurnVO;
import com.smartmall.agent.service.AgentLogService;
import com.smartmall.agent.service.ConversationService;
import com.smartmall.agent.service.PromptService;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.agent.tool.AgentToolService;
import com.smartmall.user.service.UserService;
import com.smartmall.auth.AdminOnly;
import com.smartmall.common.ApiResponse;
import com.smartmall.common.PageResult;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Category;
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
    private final AfterSaleService afterSaleService;
    private final ConversationService conversationService;
    private final PromptService promptService;
    private final LlmService llmService;
    private final AgentToolService agentToolService;
    private final UserService userService;

    @GetMapping("/stats")
    public ApiResponse<DashboardVO> stats() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(adminService.categories());
    }

    @PostMapping("/category")
    public ApiResponse<Category> createCategory(@Valid @RequestBody AdminCategoryRequest request) {
        return ApiResponse.ok(adminService.createCategory(request));
    }

    @PutMapping("/category/{id}")
    public ApiResponse<Category> updateCategory(@PathVariable String id,
                                                @Valid @RequestBody AdminCategoryRequest request) {
        return ApiResponse.ok(adminService.updateCategory(id, request));
    }

    @DeleteMapping("/category/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        adminService.deleteCategory(id);
        return ApiResponse.ok();
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
    public ApiResponse<OrderVO> ship(@PathVariable String id,
                                     @RequestBody(required = false) com.smartmall.order.dto.ShipRequest request) {
        return ApiResponse.ok(orderService.adminShip(id, request));
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

    /** 管理员重置用户密码，返回临时密码（仅本次显示） */
    @PostMapping("/user/{id}/reset-password")
    public ApiResponse<String> resetUserPassword(@PathVariable String id) {
        return ApiResponse.ok(userService.adminResetPassword(id));
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

    @GetMapping("/reviews")
    public ApiResponse<PageResult<ReviewAdminVO>> reviews(@RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.reviews(status, keyword, page, size));
    }

    @PutMapping("/review/{id}/status")
    public ApiResponse<Void> updateReviewStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateReviewStatus(id, status);
        return ApiResponse.ok();
    }

    @GetMapping("/after-sales")
    public ApiResponse<PageResult<AfterSaleVO>> afterSales(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(afterSaleService.adminList(status, page, size));
    }

    @PutMapping("/after-sale/{id}/handle")
    public ApiResponse<AfterSaleVO> handleAfterSale(@PathVariable String id,
                                                    @Valid @RequestBody HandleAfterSaleRequest request) {
        return ApiResponse.ok(afterSaleService.handle(id, request));
    }

    @GetMapping("/agent/prompts")
    public ApiResponse<List<AgentPromptVO>> prompts() {
        return ApiResponse.ok(promptService.list());
    }

    @PutMapping("/agent/prompts/{key}")
    public ApiResponse<AgentPromptVO> updatePrompt(@PathVariable String key,
                                                   @Valid @RequestBody UpdateAgentPromptRequest request) {
        return ApiResponse.ok(promptService.save(key, request.getContent()));
    }

    @PostMapping("/agent/prompts/{key}/reset")
    public ApiResponse<Void> resetPrompt(@PathVariable String key) {
        promptService.reset(key);
        return ApiResponse.ok();
    }

    @GetMapping("/agent/tools")
    public ApiResponse<List<AgentToolVO>> tools() {
        return ApiResponse.ok(agentToolService.list());
    }

    @GetMapping("/agent/model")
    public ApiResponse<LlmService.LlmStatus> model() {
        return ApiResponse.ok(llmService.status());
    }

    @PutMapping("/agent/model")
    public ApiResponse<LlmService.LlmStatus> updateModel(@Valid @RequestBody UpdateModelConfigRequest request) {
        llmService.reconfigure(request.getModel(), request.getTemperature());
        return ApiResponse.ok(llmService.status());
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResult<AdminConversationVO>> conversations(@RequestParam(required = false) String keyword,
                                                                      @RequestParam(defaultValue = "1") long page,
                                                                      @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.conversations(keyword, page, size));
    }

    @GetMapping("/conversations/{id}/turns")
    public ApiResponse<List<TurnVO>> conversationTurns(@PathVariable String id) {
        return ApiResponse.ok(conversationService.turnsForAdmin(id));
    }
}
