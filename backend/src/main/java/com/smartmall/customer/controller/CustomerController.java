package com.smartmall.customer.controller;

import com.smartmall.auth.AuthContext;
import com.smartmall.common.ApiResponse;
import com.smartmall.customer.dto.CsConversationVO;
import com.smartmall.customer.dto.CsMessageVO;
import com.smartmall.customer.entity.CsConversation;
import com.smartmall.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客服会话接口。实时收发走 /ws/cs，这里负责会话开启、历史消息与未读。
 */
@RestController
@RequestMapping("/api/cs")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** 买家从订单详情发起联系卖家 */
    @PostMapping("/open")
    public ApiResponse<CsConversationVO> open(@RequestBody Map<String, String> body) {
        String orderId = body.get("orderId");
        String buyerId = AuthContext.userId();
        CsConversation conv = customerService.openByOrder(orderId, buyerId);
        return ApiResponse.ok(CsConversationVO.of(conv, buyerId));
    }

    /** 我的会话列表（买家与卖家共用，按角色返回未读口径） */
    @GetMapping("/conversations")
    public ApiResponse<List<CsConversationVO>> conversations() {
        String userId = AuthContext.userId();
        return ApiResponse.ok(customerService.listMine(userId).stream()
                .map(c -> CsConversationVO.of(c, userId))
                .toList());
    }

    /** 会话历史消息 */
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<CsMessageVO>> messages(@PathVariable String id) {
        String userId = AuthContext.userId();
        return ApiResponse.ok(customerService.messages(id, userId).stream()
                .map(CsMessageVO::of)
                .toList());
    }

    /** 进入会话，清除自己的未读 */
    @PostMapping("/conversations/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        customerService.markRead(id, AuthContext.userId());
        return ApiResponse.ok();
    }

    /** 卖家未读总数，用于侧栏角标 */
    @GetMapping("/unread")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("count", customerService.sellerUnreadTotal(AuthContext.userId())));
    }
}