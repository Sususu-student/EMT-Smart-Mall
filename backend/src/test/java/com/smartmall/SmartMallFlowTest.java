package com.smartmall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmall.seed.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路冒烟测试：登录 → 多轮对话（澄清/检索/推荐）→ 对比 → 购物车下单 → 支付 → 后台统计。
 * 无 API Key 时走规则引擎，保证 CI 与离线环境可运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartMallFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSeeder dataSeeder;

    @Test
    void fullJourney() throws Exception {
        dataSeeder.run(null);

        String token = login("demo", "Demo@123");
        String adminToken = login("admin", "Admin@123");

        JsonNode productPage = getJson("/api/product/list?page=1&size=8&sort=sales", token);
        assertThat(productPage.path("total").asInt()).isGreaterThanOrEqualTo(100);
        assertThat(productPage.path("records").size()).isEqualTo(8);

        JsonNode firstChat = chat(token, "我想买个耳机，跑步用", null);
        assertThat(firstChat.path("intent").asText()).isEqualTo("clarify");
        assertThat(firstChat.path("reply").asText()).contains("预算");
        String conversationId = firstChat.path("conversationId").asText();
        assertThat(conversationId).isNotBlank();
        assertThat(firstChat.path("steps").size()).isGreaterThan(2);

        JsonNode secondChat = chat(token, "300以内，要防水", conversationId);
        JsonNode products = secondChat.path("products");
        assertThat(products.size()).isGreaterThanOrEqualTo(2);
        List<String> ids = new ArrayList<>();
        products.forEach(item -> ids.add(item.path("product").path("id").asText()));
        String firstReason = products.get(0).path("reason").asText();
        assertThat(firstReason).contains("售价");
        assertThat(products.get(0).path("product").path("attributes").has("防水等级")).isTrue();

        JsonNode compare = postJson("/api/agent/compare",
                objectMapper.createObjectNode().set("productIds", objectMapper.valueToTree(ids.subList(0, 2))),
                null);
        assertThat(compare.path("recommendedId").asText()).isNotBlank();
        assertThat(compare.path("dimensions").size()).isGreaterThanOrEqualTo(4);
        assertThat(compare.path("scores").size()).isEqualTo(2);
        assertThat(compare.path("advice").asText()).isNotBlank();

        JsonNode cartChat = chat(token, "把第一款加入购物车", conversationId);
        assertThat(cartChat.path("intent").asText()).isEqualTo("add_to_cart");
        assertThat(cartChat.path("reply").asText()).contains("购物车");

        JsonNode cart = getJson("/api/cart", token);
        assertThat(cart.path("totalQuantity").asInt()).isGreaterThan(0);
        String addressId = getJson("/api/user/address", token).get(0).path("id").asText();
        assertThat(addressId).isNotBlank();

        var orderBody = objectMapper.createObjectNode();
        orderBody.put("addressId", addressId);
        orderBody.put("remark", "集成测试订单");
        JsonNode order = postJson("/api/order", orderBody, token);
        String orderId = order.path("id").asText();
        assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");

        JsonNode paid = putJson("/api/order/" + orderId + "/pay", null, token);
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("totalAmount").asDouble()).isGreaterThan(0);

        JsonNode detail = getJson("/api/order/" + orderId, token);
        assertThat(detail.path("statusLabel").asText()).isEqualTo("已支付");
        assertThat(detail.path("items").size()).isGreaterThan(0);

        JsonNode orderChat = chat(token, "我的订单到哪了", conversationId);
        assertThat(orderChat.path("intent").asText()).isEqualTo("query_order");
        assertThat(orderChat.path("orders").size()).isGreaterThan(0);
        assertThat(orderChat.path("reply").asText()).contains("已支付");

        JsonNode logs = getJson("/api/agent/logs?taskId=" + firstChat.path("taskId").asText(), token);
        assertThat(logs.size()).isGreaterThan(3);
        assertThat(logs.get(0).path("agentId").asText()).isEqualTo("guide-agent");

        JsonNode stats = getJson("/api/admin/stats", adminToken);
        assertThat(stats.path("orderCount").asLong()).isGreaterThan(0);
        assertThat(stats.path("agentLogCount").asLong()).isGreaterThan(0);
        assertThat(stats.path("topProducts").size()).isGreaterThan(0);

        JsonNode adminLogs = getJson("/api/admin/agent-logs?page=1&size=20", adminToken);
        assertThat(adminLogs.path("total").asLong()).isGreaterThan(3);

        var importItem = objectMapper.createObjectNode();
        importItem.put("name", "批量导入测试商品");
        importItem.put("categoryId", "cat_headphone");
        importItem.put("price", 199);
        importItem.put("stock", 20);
        var importBody = objectMapper.createArrayNode().add(importItem);
        JsonNode importResult = postJson("/api/admin/products/import", importBody, adminToken);
        assertThat(importResult.path("success").asInt()).isEqualTo(1);
        assertThat(importResult.path("failed").asInt()).isZero();
    }

    private String login(String username, String password) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("username", username);
        body.put("password", password);
        JsonNode data = postJson("/api/user/login", body, null);
        String token = data.path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private JsonNode chat(String token, String message, String conversationId) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("message", message);
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }
        return postJson("/api/agent/chat", body, token);
    }

    private JsonNode getJson(String url, String token) throws Exception {
        var request = get(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode postJson(String url, JsonNode body, String token) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode putJson(String url, JsonNode body, String token) throws Exception {
        var request = put(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.path("code").asInt()).isZero();
        return root.path("data");
    }
}
