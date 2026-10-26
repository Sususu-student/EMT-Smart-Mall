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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        assertThat(secondChat.path("slots").path("maxPrice").asInt()).isEqualTo(300);
        assertThat(secondChat.path("slots").path("categoryName").asText()).isEqualTo("蓝牙耳机");
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
        orderBody.put("deliveryMethod", "EXPRESS");
        JsonNode order = postJson("/api/order", orderBody, token);
        String orderId = order.path("id").asText();
        assertThat(order.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("deliveryMethod").asText()).isEqualTo("EXPRESS");
        assertThat(order.path("deliveryFee").asDouble()).isEqualTo(12.0);

        var paymentRequest = objectMapper.createObjectNode();
        paymentRequest.put("channel", "ALIPAY");
        JsonNode payment = postJson("/api/payment/orders/" + orderId, paymentRequest, token);
        assertThat(payment.path("status").asText()).isEqualTo("PENDING");
        assertThat(payment.path("paymentNo").asText()).isNotBlank();
        var callbackBody = objectMapper.createObjectNode();
        callbackBody.put("paymentNo", payment.path("paymentNo").asText());
        callbackBody.put("success", true);
        JsonNode paymentCallback = postJson("/api/payment/callback", callbackBody, null);
        assertThat(paymentCallback.path("status").asText()).isEqualTo("SUCCESS");
        JsonNode duplicateCallback = postJson("/api/payment/callback", callbackBody, null);
        assertThat(duplicateCallback.path("status").asText()).isEqualTo("SUCCESS");

        JsonNode detail = getJson("/api/order/" + orderId, token);
        assertThat(detail.path("status").asText()).isEqualTo("PAID");
        assertThat(detail.path("statusLabel").asText()).isEqualTo("已支付");
        assertThat(detail.path("paymentNo").asText()).isEqualTo(payment.path("paymentNo").asText());
        assertThat(detail.path("paymentChannel").asText()).isEqualTo("ALIPAY");

        JsonNode unread = getJson("/api/notification/unread-count", token);
        assertThat(unread.asLong()).isGreaterThan(0);
        assertThat(detail.path("items").size()).isGreaterThan(0);

        JsonNode orderChat = chat(token, "我的订单到哪了", conversationId);
        assertThat(orderChat.path("intent").asText()).isEqualTo("query_order");
        assertThat(orderChat.path("orders").size()).isGreaterThan(0);
        assertThat(orderChat.path("reply").asText()).contains("已支付");

        var shipBody = objectMapper.createObjectNode();
        shipBody.put("carrier", "顺丰速运");
        shipBody.put("trackingNo", "SF1234567890");
        JsonNode shipped = putJson("/api/admin/order/" + orderId + "/ship", shipBody, adminToken);
        assertThat(shipped.path("status").asText()).isEqualTo("SHIPPED");
        assertThat(shipped.path("carrier").asText()).isEqualTo("顺丰速运");
        assertThat(shipped.path("trackingNo").asText()).isEqualTo("SF1234567890");

        JsonNode completed = putJson("/api/order/" + orderId + "/confirm", null, token);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");

        var afterSaleBody = objectMapper.createObjectNode();
        afterSaleBody.put("orderId", orderId);
        afterSaleBody.put("type", "RETURN");
        afterSaleBody.put("reason", "商品与描述不符，申请退货");
        JsonNode afterSale = postJson("/api/after-sale", afterSaleBody, token);
        assertThat(afterSale.path("status").asText()).isEqualTo("PENDING");
        JsonNode adminAfterSales = getJson("/api/admin/after-sales?page=1&size=10", adminToken);
        assertThat(adminAfterSales.path("total").asLong()).isGreaterThan(0);
        var handleBody = objectMapper.createObjectNode();
        handleBody.put("status", "APPROVED");
        handleBody.put("adminNote", "同意退货，请寄回商品");
        JsonNode handled = putJson("/api/admin/after-sale/" + afterSale.path("id").asText() + "/handle",
                handleBody, adminToken);
        assertThat(handled.path("status").asText()).isEqualTo("APPROVED");

        JsonNode adminConversations = getJson("/api/admin/conversations?page=1&size=10", adminToken);
        assertThat(adminConversations.path("total").asLong()).isGreaterThan(0);
        String adminConversationId = adminConversations.path("records").get(0).path("id").asText();
        assertThat(getJson("/api/admin/conversations/" + adminConversationId + "/turns", adminToken).size())
                .isGreaterThan(0);

        JsonNode notifications = getJson("/api/notification?page=1&size=20", token);
        assertThat(notifications.path("total").asLong()).isGreaterThan(0);
        assertThat(notifications.path("records").size()).isGreaterThan(0);
        long notificationId = notifications.path("records").get(0).path("id").asLong();
        mvc.perform(put("/api/notification/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(put("/api/notification/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode logs = getJson("/api/agent/logs?taskId=" + firstChat.path("taskId").asText(), token);
        assertThat(logs.size()).isGreaterThan(3);
        assertThat(logs.get(0).path("agentId").asText()).isEqualTo("guide-agent");

        JsonNode stats = getJson("/api/admin/stats", adminToken);
        assertThat(stats.path("orderCount").asLong()).isGreaterThan(0);
        assertThat(stats.path("agentLogCount").asLong()).isGreaterThan(0);
        assertThat(stats.path("topProducts").size()).isGreaterThan(0);

        JsonNode preferenceChat = chat(token, "推荐蓝牙耳机，预算500以内", null);
        assertThat(preferenceChat.path("appliedPreferences").size()).isGreaterThan(0);
        assertThat(preferenceChat.path("slots").path("scene").asText()).isEqualTo("跑步");

        JsonNode chineseChat = chat(token, "三百元以内的蓝牙耳机", null);
        assertThat(chineseChat.path("intent").asText()).isEqualTo("search_product");
        assertThat(chineseChat.path("slots").path("maxPrice").asInt()).isEqualTo(300);
        assertThat(chineseChat.path("products").size()).isGreaterThanOrEqualTo(1);

        JsonNode ratingProduct = getJson("/api/product/p_yoga_05", token);
        JsonNode ratingReviews = getJson("/api/product/p_yoga_05/reviews?page=1&size=50", token);
        double reviewAverage = 0;
        for (JsonNode review : ratingReviews.path("records")) {
            reviewAverage += review.path("rating").asDouble();
        }
        reviewAverage /= ratingReviews.path("records").size();
        assertThat(reviewAverage).isCloseTo(ratingProduct.path("rating").asDouble(),
                org.assertj.core.data.Offset.offset(0.05));

        var crossCategory = objectMapper.createObjectNode();
        crossCategory.set("productIds", objectMapper.valueToTree(List.of("p_headphone_01", "p_coffee_01")));
        mvc.perform(post("/api/agent/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossCategory)))
                .andExpect(status().isBadRequest());

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

        JsonNode reviews = getJson("/api/admin/reviews?page=1&size=10", adminToken);
        assertThat(reviews.path("total").asLong()).isGreaterThan(0);
        long reviewId = reviews.path("records").get(0).path("id").asLong();
        mvc.perform(put("/api/admin/review/" + reviewId + "/status?status=0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/review/" + reviewId + "/status?status=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode categories = getJson("/api/admin/categories", adminToken);
        assertThat(categories.size()).isGreaterThanOrEqualTo(21);
        var categoryBody = objectMapper.createObjectNode();
        categoryBody.put("name", "临时测试分类");
        categoryBody.put("icon", "🧪");
        categoryBody.put("sortOrder", 99);
        JsonNode category = postJson("/api/admin/category", categoryBody, adminToken);
        assertThat(category.path("name").asText()).isEqualTo("临时测试分类");
        mvc.perform(delete("/api/admin/category/" + category.path("id").asText())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        JsonNode agentPrompts = getJson("/api/admin/agent/prompts", adminToken);
        assertThat(agentPrompts.size()).isGreaterThanOrEqualTo(5);
        String promptKey = agentPrompts.get(0).path("key").asText();
        var promptBody = objectMapper.createObjectNode();
        promptBody.put("content", "你是测试用 Prompt，只输出 JSON。");
        JsonNode updatedPrompt = putJson("/api/admin/agent/prompts/" + promptKey, promptBody, adminToken);
        assertThat(updatedPrompt.path("customized").asBoolean()).isTrue();
        mvc.perform(post("/api/admin/agent/prompts/" + promptKey + "/reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(getJson("/api/admin/agent/tools", adminToken).size()).isGreaterThanOrEqualTo(8);
        JsonNode modelStatus = getJson("/api/admin/agent/model", adminToken);
        assertThat(modelStatus.path("model").asText()).isEqualTo("deepseek-v4.1-flash");
        var modelBody = objectMapper.createObjectNode();
        modelBody.put("model", "deepseek-v4.1-flash");
        modelBody.put("temperature", 0.5);
        JsonNode changedModel = putJson("/api/admin/agent/model", modelBody, adminToken);
        assertThat(changedModel.path("temperature").asDouble()).isEqualTo(0.5);

        var addCartBody = objectMapper.createObjectNode();
        addCartBody.put("productId", "p_headphone_02");
        addCartBody.put("quantity", 1);
        postJson("/api/cart", addCartBody, token);
        JsonNode checkoutPreview = chat(token, "帮我下单", null);
        assertThat(checkoutPreview.path("intent").asText()).isEqualTo("checkout");
        assertThat(checkoutPreview.path("reply").asText()).contains("确认下单");
        String checkoutConversation = checkoutPreview.path("conversationId").asText();
        JsonNode checkoutConfirmed = chat(token, "确认下单", checkoutConversation);
        assertThat(checkoutConfirmed.path("intent").asText()).isEqualTo("checkout");
        assertThat(checkoutConfirmed.path("orders").size()).isGreaterThanOrEqualTo(1);
        assertThat(checkoutConfirmed.path("orders").get(0).path("orderNo").asText()).isNotBlank();
        assertThat(checkoutConfirmed.path("orders").get(0).path("deliveryMethod").asText()).isEqualTo("STANDARD");

        // ===== 卖家中心：注册卖家、发布商品、归属校验 =====
        var sellerRegBody = objectMapper.createObjectNode();
        sellerRegBody.put("username", "seller_test");
        sellerRegBody.put("password", "Seller@123");
        sellerRegBody.put("nickname", "测试卖家");
        sellerRegBody.put("role", "SELLER");
        JsonNode sellerReg = postJson("/api/user/register", sellerRegBody, null);
        assertThat(sellerReg.path("user").path("role").asText()).isEqualTo("SELLER");
        String sellerToken = sellerReg.path("token").asText();

        var publishBody = objectMapper.createObjectNode();
        publishBody.put("name", "测试卖家商品");
        publishBody.put("categoryId", "cat_headphone");
        publishBody.put("price", 199);
        publishBody.put("stock", 20);
        JsonNode published = postJson("/api/seller/products", publishBody, sellerToken);
        String sellerProductId = published.path("id").asText();
        assertThat(published.path("sellerName").asText()).isEqualTo("测试卖家");

        // 卖家只能看到自己的商品
        JsonNode myProducts = getJson("/api/seller/products?page=1&size=10", sellerToken);
        assertThat(myProducts.path("total").asLong()).isEqualTo(1);

        // 买家访问卖家接口被拒绝
        mvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 卖家不能修改别人的商品
        var otherSellerBody = objectMapper.createObjectNode();
        otherSellerBody.put("username", "seller_other");
        otherSellerBody.put("password", "Seller@123");
        otherSellerBody.put("role", "SELLER");
        String otherToken = postJson("/api/user/register", otherSellerBody, null).path("token").asText();
        mvc.perform(put("/api/seller/products/" + sellerProductId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishBody)))
                .andExpect(status().isForbidden());

        // 商品详情带出卖家信息
        JsonNode sellerProductDetail = getJson("/api/product/" + sellerProductId, null);
        assertThat(sellerProductDetail.path("sellerId").asText()).isNotBlank();
        assertThat(sellerProductDetail.path("sellerName").asText()).isEqualTo("测试卖家");

        // 商品已带真实图片外链与本地兜底（每个商品图片不同）
        JsonNode imageCheck = getJson("/api/product/p_headphone_01", null);
        assertThat(imageCheck.path("image").asText()).contains("images.unsplash.com");
        assertThat(imageCheck.path("imageFallback").asText()).startsWith("/images/catalog/");

        // 同品类不同商品应使用不同图片
        JsonNode secondProduct = getJson("/api/product/p_headphone_02", null);
        assertThat(secondProduct.path("image").asText())
                .isNotEqualTo(imageCheck.path("image").asText());

        // ===== 忘记密码：用户名 + 邮箱自助重置 =====
        var resetBody = objectMapper.createObjectNode();
        resetBody.put("username", "seller_test");
        resetBody.put("email", "seller_test@smartmall.dev");
        resetBody.put("newPassword", "NewPass@123");
        // 注册时未填邮箱，这里应报不匹配
        mvc.perform(post("/api/user/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetBody)))
                .andExpect(status().isBadRequest());

        // 给测试卖家补一个邮箱后再重置
        var profileBody = objectMapper.createObjectNode();
        profileBody.put("email", "seller_test@smartmall.dev");
        putJson("/api/user/profile", profileBody, sellerToken);
        mvc.perform(post("/api/user/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetBody)))
                .andExpect(status().isOk());
        // 用新密码可以登录
        var reloginBody = objectMapper.createObjectNode();
        reloginBody.put("username", "seller_test");
        reloginBody.put("password", "NewPass@123");
        assertThat(postJson("/api/user/login", reloginBody, null).path("token").asText()).isNotBlank();

        // ===== 管理员重置指定用户密码 =====
        var adminResetBody = objectMapper.createObjectNode();
        String tempPassword = postJson("/api/admin/user/" + sellerReg.path("user").path("id").asText()
                + "/reset-password", adminResetBody, adminToken).asText();
        assertThat(tempPassword).isNotBlank();
        // 用临时密码可以登录
        var tempLogin = objectMapper.createObjectNode();
        tempLogin.put("username", "seller_test");
        tempLogin.put("password", tempPassword);
        assertThat(postJson("/api/user/login", tempLogin, null).path("token").asText()).isNotBlank();

        // ===== 图片上传：卖家可传，买家被拒，非图片被拒 =====
        var imageFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.png", "image/png", "fake-image-bytes".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/upload/image").file(imageFile)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/upload/image").file(imageFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        var textFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "bad.txt", "text/plain", "not an image".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/upload/image").file(textFile)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isBadRequest());

        // ===== 卖家发货闭环：买家下单 → 卖家发货 → 买家看到已发货 =====
        // 卖家把自己的商品补足库存
        var restockBody = objectMapper.createObjectNode();
        restockBody.put("name", "测试卖家商品");
        restockBody.put("categoryId", "cat_headphone");
        restockBody.put("price", 199);
        restockBody.put("stock", 50);
        putJson("/api/seller/products/" + sellerProductId, restockBody, sellerToken);

        var addressBody = objectMapper.createObjectNode();
        addressBody.put("receiver", "发货测试买家");
        addressBody.put("phone", "13800002222");
        addressBody.put("province", "广东省");
        addressBody.put("city", "深圳市");
        addressBody.put("district", "南山区");
        addressBody.put("detail", "测试路 1 号");
        addressBody.put("isDefault", 1);
        String sellerTestAddressId = postJson("/api/user/address", addressBody, token).path("id").asText();

        var shipOrderBody = objectMapper.createObjectNode();
        shipOrderBody.put("productId", sellerProductId);
        shipOrderBody.put("quantity", 2);
        shipOrderBody.put("addressId", sellerTestAddressId);
        String shipOrderId = postJson("/api/order", shipOrderBody, token).path("id").asText();
        putJson("/api/order/" + shipOrderId + "/pay", objectMapper.createObjectNode(), token);

        // 卖家首页应出现待发货统计
        JsonNode overview = getJson("/api/seller/overview", sellerToken);
        assertThat(overview.path("pendingShipOrders").asLong()).isEqualTo(1);
        assertThat(overview.path("pendingShipItems").asLong()).isEqualTo(2);

        // 待发货列表能看到收货信息
        JsonNode pendingOrders = getJson("/api/seller/orders?shipStatus=PENDING&page=1&size=10", sellerToken);
        assertThat(pendingOrders.path("total").asLong()).isEqualTo(1);
        JsonNode firstOrder = pendingOrders.path("records").get(0);
        assertThat(firstOrder.path("shipStatusLabel").asText()).isEqualTo("待发货");
        assertThat(firstOrder.path("receiver").asText()).isEqualTo("发货测试买家");
        assertThat(firstOrder.path("myItemCount").asInt()).isEqualTo(2);

        // 订单详情含商品明细
        JsonNode sellerOrderDetail = getJson("/api/seller/orders/" + shipOrderId, sellerToken);
        assertThat(sellerOrderDetail.path("items").size()).isEqualTo(1);
        assertThat(sellerOrderDetail.path("items").get(0).path("shipStatus").asText()).isEqualTo("PENDING");

        // 买家不能发货
        mvc.perform(post("/api/seller/orders/" + shipOrderId + "/ship")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 卖家发货
        var sellerShipBody = objectMapper.createObjectNode();
        sellerShipBody.put("carrier", "顺丰速运");
        sellerShipBody.put("trackingNo", "SFTEST0001");
        JsonNode sellerShipped = postJson("/api/seller/orders/" + shipOrderId + "/ship", sellerShipBody, sellerToken);
        assertThat(sellerShipped.path("shippedCount").asInt()).isEqualTo(1);
        assertThat(sellerShipped.path("trackingNo").asText()).isEqualTo("SFTEST0001");
        assertThat(sellerShipped.path("orderStatus").asText()).isEqualTo("SHIPPED");

        // 买家侧订单变为已发货
        JsonNode buyerOrder = getJson("/api/order/" + shipOrderId, token);
        assertThat(buyerOrder.path("status").asText()).isEqualTo("SHIPPED");

        // 重复发货被拒绝
        mvc.perform(post("/api/seller/orders/" + shipOrderId + "/ship")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isBadRequest());

        // 已发货筛选能查到，待发货筛选查不到
        assertThat(getJson("/api/seller/orders?shipStatus=SHIPPED&page=1&size=10", sellerToken)
                .path("total").asLong()).isEqualTo(1);
        assertThat(getJson("/api/seller/orders?shipStatus=PENDING&page=1&size=10", sellerToken)
                .path("total").asLong()).isEqualTo(0);

        // 别的卖家不能看/发这个订单
        var otherLogin = objectMapper.createObjectNode();
        otherLogin.put("username", "seller_other");
        otherLogin.put("password", "Seller@123");
        String otherSellerToken = postJson("/api/user/login", otherLogin, null).path("token").asText();
        mvc.perform(get("/api/seller/orders/" + shipOrderId)
                        .header("Authorization", "Bearer " + otherSellerToken))
                .andExpect(status().isForbidden());

        var passwordBody = objectMapper.createObjectNode();
        passwordBody.put("oldPassword", "Demo@123");
        passwordBody.put("newPassword", "Demo@1234");
        mvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordBody)))
                .andExpect(status().isOk());
        String reloginToken = login("demo", "Demo@1234");
        assertThat(reloginToken).isNotBlank();
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
