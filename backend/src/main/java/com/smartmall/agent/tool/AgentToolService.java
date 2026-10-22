package com.smartmall.agent.tool;

import com.smartmall.agent.dto.AgentToolVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具清单（只读注册表）：暴露 Agent 当前可调用的后端工具，供后台查看。
 * 工具本身是 Spring Bean，运行期新增工具需要写代码，避免后台误配置造成越权。
 */
@Service
public class AgentToolService {

    public List<AgentToolVO> list() {
        return List.of(
                new AgentToolVO("searchProducts", "按品类、价格、关键词和功能偏好检索商品", List.of("categoryId", "minPrice", "maxPrice", "keyword", "features"), "导购 Agent", "SEARCH"),
                new AgentToolVO("getProductDetail", "读取商品真实参数、价格、库存和评价", List.of("productId"), "导购 Agent", "READ"),
                new AgentToolVO("compareProducts", "构建对比矩阵并计算加权得分", List.of("productIds", "weights"), "对比 Agent", "COMPUTE"),
                new AgentToolVO("queryOrder", "查询用户订单状态与物流单号", List.of("userId", "orderNo"), "导购 Agent", "READ"),
                new AgentToolVO("addToCart", "将指定商品加入购物车", List.of("userId", "productId", "quantity"), "导购 Agent", "WRITE"),
                new AgentToolVO("getCartItems", "查询用户购物车内容", List.of("userId"), "导购 Agent", "READ"),
                new AgentToolVO("getUserPreference", "读取用户品类、场景、品牌偏好", List.of("userId"), "导购 Agent", "READ"),
                new AgentToolVO("changePassword", "校验原密码并修改登录密码", List.of("oldPassword", "newPassword"), "用户中心", "WRITE"),
                new AgentToolVO("createAfterSale", "为已支付/已完成订单创建售后申请", List.of("orderId", "type", "reason"), "用户中心", "WRITE")
        );
    }
}
