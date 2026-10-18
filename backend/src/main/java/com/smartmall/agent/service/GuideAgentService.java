package com.smartmall.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.agent.Intents;
import com.smartmall.agent.dto.AgentEvent;
import com.smartmall.agent.dto.AgentStep;
import com.smartmall.agent.dto.ChatRequest;
import com.smartmall.agent.dto.ChatResponse;
import com.smartmall.agent.dto.CompareRequest;
import com.smartmall.agent.dto.CompareResponse;
import com.smartmall.agent.dto.IntentResult;
import com.smartmall.agent.dto.RecommendedProduct;
import com.smartmall.agent.dto.ScoredProduct;
import com.smartmall.agent.dto.Slots;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.agent.tool.ProductSearchTool;
import com.smartmall.agent.ws.AgentWebSocketHandler;
import com.smartmall.auth.AuthContext;
import com.smartmall.cart.dto.AddCartRequest;
import com.smartmall.cart.dto.CartSummaryVO;
import com.smartmall.cart.service.CartService;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.entity.Order;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.service.ProductService;
import com.smartmall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuideAgentService {

    private final ConversationService conversationService;
    private final IntentService intentService;
    private final SlotService slotService;
    private final ProductSearchTool searchTool;
    private final RecommendationService recommendationService;
    private final CompareAgentService compareAgentService;
    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;
    private final AgentLogService logService;
    private final LlmService llmService;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final AgentWebSocketHandler webSocketHandler;

    private static final Pattern ORDER_NO = Pattern.compile("SG\\d{10,}");
    private static final Pattern QUANTITY = Pattern.compile("(\\d+)\\s*(?:件|个|只|双|台|盒|瓶)");

    public ChatResponse chat(ChatRequest request) {
        String userId = AuthContext.userId();
        String taskId = Ids.next();
        String message = request.getMessage().trim();
        var conversation = conversationService.getOrCreate(userId, request.getConversationId(), message);

        ChatResponse response = new ChatResponse();
        response.setConversationId(conversation.getId());
        response.setTaskId(taskId);
        response.setDegraded(!llmService.isAvailable());
        List<AgentStep> steps = new ArrayList<>();

        var memoryTrace = logService.start(taskId, conversation.getId(), "guide-agent", "load_memory",
                "加载会话记忆", "conversationId=" + conversation.getId());
        Slots slots = parseSlots(conversation.getSlots());
        List<String> context = conversationService.recentContext(conversation.getId(), 10);
        steps.add(memoryTrace.success("加载 " + context.size() + " 条历史对话"));
        response.setMemorySummary("最近 " + context.size() + " 条对话，当前槽位："
                + describeSlots(slots));

        conversationService.appendTurn(conversation.getId(), "user", message, null,
                JsonUtils.toJson(slots), null, taskId);

        try {
            var intentTrace = logService.start(taskId, conversation.getId(), "guide-agent", "intent_recognition",
                    "意图识别", message);
            IntentResult intent = intentService.detect(message, context);
            steps.add(intentTrace.success(intent.getIntent() + "（" + intent.getSource() + "，置信度 "
                    + intent.getConfidence() + "）"));

            switch (intent.getIntent()) {
                case Intents.COMPARE -> handleCompare(message, slots, conversation.getId(), taskId, userId, response, steps);
                case Intents.ORDER -> handleOrder(message, conversation.getId(), taskId, userId, response, steps);
                case Intents.ADD_TO_CART -> handleAddToCart(message, slots, conversation.getId(), taskId, response, steps);
                case Intents.DETAIL -> handleDetail(message, slots, conversation.getId(), taskId, userId, response, steps);
                case Intents.CHITCHAT -> handleChitchat(message, conversation.getId(), taskId, response, steps);
                default -> handleSearch(message, slots, context, conversation.getId(), taskId, userId, response, steps);
            }
        } catch (BizException e) {
            log.warn("导购 Agent 业务异常: {}", e.getMessage());
            response.setReply("抱歉，" + e.getMessage() + "。你可以换个说法，或直接描述品类、预算和使用场景。");
            response.setIntent(response.getIntent() == null ? Intents.CHITCHAT : response.getIntent());
            response.getClarifyOptions().addAll(List.of("我想买蓝牙耳机，预算300以内", "帮我看看我的订单"));
        } catch (Exception e) {
            log.error("导购 Agent 执行异常", e);
            response.setReply("抱歉，刚才处理时出了点问题。请稍后重试，或换一种描述方式。");
            response.setIntent(Intents.CHITCHAT);
        }

        response.setSteps(steps);
        conversationService.appendTurn(conversation.getId(), "assistant", response.getReply(),
                response.getIntent(), JsonUtils.toJson(slots), JsonUtils.toJson(response.getProducts()), taskId);
        conversationService.saveSlots(conversation.getId(), JsonUtils.toJson(slots));
        logService.start(taskId, conversation.getId(), "guide-agent", "save_memory", "保存会话与执行记录", "turn saved")
                .success("会话已保存");
        try {
            webSocketHandler.broadcast(conversation.getId(),
                    AgentEvent.done(taskId, conversation.getId(), response.getReply()));
        } catch (Exception ignored) {
        }
        return response;
    }

    private void handleSearch(String message, Slots slots, List<String> context, String conversationId,
                              String taskId, String userId, ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.SEARCH);
        var slotTrace = logService.start(taskId, conversationId, "guide-agent", "slot_extraction",
                "槽位提取", message);
        Slots extracted = slotService.extract(message, slots, context);
        copySlots(extracted, slots);
        steps.add(slotTrace.success(JsonUtils.toJson(slots)));

        if (!slots.hasCategory() && !StringUtils.hasText(slots.getKeyword())) {
            var clarifyTrace = logService.start(taskId, conversationId, "guide-agent", "clarify",
                    "澄清需求", "缺少品类");
            slots.setPending("category");
            response.setIntent(Intents.CLARIFY);
            response.setReply("为了更准确地推荐，先告诉我你想买哪类商品？比如蓝牙耳机、跑步鞋、咖啡机，或者直接描述使用场景。");
            response.getClarifyOptions().addAll(categoryOptions());
            steps.add(clarifyTrace.success("追问品类"));
            return;
        }
        if (slots.hasCategory() && !slots.hasBudget() && !slots.isAskedBudget()) {
            var clarifyTrace = logService.start(taskId, conversationId, "guide-agent", "clarify",
                    "澄清需求", "缺少预算");
            slots.setAskedBudget(true);
            slots.setPending("budget");
            response.setIntent(Intents.CLARIFY);
            response.setReply("你的预算大概是多少？我可以在预算范围内按防水、续航、评分等维度帮你筛选。");
            response.getClarifyOptions().addAll(List.of("200 元以内", "300 元以内", "300-500 元", "500 元以上", "预算不限"));
            steps.add(clarifyTrace.success("追问预算"));
            return;
        }

        var searchTrace = logService.start(taskId, conversationId, "guide-agent", "search_products",
                "商品检索", JsonUtils.toJson(slots));
        ProductSearchTool.SearchOutcome outcome = searchTool.search(slots, 5);
        steps.add(searchTrace.success(outcome.note()));
        if (outcome.products().isEmpty()) {
            response.setReply("没有找到完全符合条件的商品。可以适当提高预算、换个关键词，或看看这些品类：" );
            response.getClarifyOptions().addAll(List.of("预算不限", "换个品类看看", "蓝牙耳机", "跑步鞋"));
            response.setIntent(Intents.SEARCH);
            return;
        }

        var recommendTrace = logService.start(taskId, conversationId, "guide-agent", "generate_reasons",
                "推荐理由生成", "候选 " + outcome.products().size() + " 件");
        RecommendationService.Result recommendations = recommendationService.generate(outcome.products(), slots);
        steps.add(recommendTrace.success("生成 " + recommendations.items().size() + " 条理由，拦截 "
                + recommendations.issues().size() + " 条不合规内容"));
        if (!recommendations.issues().isEmpty()) {
            log.info("推荐理由校验记录: {}", recommendations.issues());
        }

        var validateTrace = logService.start(taskId, conversationId, "guide-agent", "field_validation",
                "字段校验", "价格/库存/属性一致性");
        long fallbackCount = recommendations.items().stream().filter(RecommendedProduct::isReasonFallback).count();
        steps.add(validateTrace.success("通过校验 " + recommendations.items().size() + " 条，模板兜底 " + fallbackCount + " 条"));

        response.setProducts(recommendations.items());
        slots.rememberProducts(recommendations.items().stream().map(i -> i.getProduct().getId()).toList(),
                recommendations.items().stream().map(i -> i.getProduct().getName()).toList());
        slots.setPending(null);
        response.setReply(buildSearchReply(slots, recommendations.items().size(), outcome));

        if (StringUtils.hasText(slots.getCategoryName())) {
            userService.learnPreference(userId, "品类", slots.getCategoryName(), BigDecimal.valueOf(0.8));
        }
        if (slots.getMaxPrice() != null) {
            userService.learnPreference(userId, "预算", slots.getMaxPrice().stripTrailingZeros().toPlainString(),
                    BigDecimal.valueOf(0.7));
        }
        if (StringUtils.hasText(slots.getScene())) {
            userService.learnPreference(userId, "场景", slots.getScene(), BigDecimal.valueOf(0.8));
        }
    }

    private void handleCompare(String message, Slots slots, String conversationId, String taskId,
                               String userId, ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.COMPARE);
        List<String> ids = slotService.referencedIds(message, slots);
        if (ids.size() < 2 && slots.getLastProductIds().size() >= 2) {
            ids = slots.getLastProductIds().stream().limit(3).toList();
        }
        if (ids.size() < 2) {
            response.setReply("我需要至少两件商品才能对比。你可以先描述需求让我推荐，再说“对比前两个”；也可以从商品列表勾选后进入对比页。");
            response.getClarifyOptions().addAll(slots.getLastProductNames().isEmpty()
                    ? List.of("我想买蓝牙耳机，预算300以内") : slots.getLastProductNames());
            var clarifyTrace = logService.start(taskId, conversationId, "guide-agent", "clarify",
                    "澄清对比对象", "商品不足 2 件");
            steps.add(clarifyTrace.success("追问对比对象"));
            return;
        }
        var compareTrace = logService.start(taskId, conversationId, "guide-agent", "call_compare_agent",
                "调用对比决策 Agent", "商品: " + String.join(",", ids));
        CompareRequest compareRequest = new CompareRequest();
        compareRequest.setProductIds(ids);
        compareRequest.setUsePreference(true);
        CompareResponse compare = compareAgentService.compare(compareRequest, userId, taskId, conversationId);
        steps.add(compareTrace.success("推荐 " + compare.getRecommendedName()
                + "，得分 " + compare.getScores().get(0).getTotalScore()));
        response.setCompare(compare);
        response.setReply("已完成 " + compare.getProducts().size()
                + " 款商品的对比，综合评分与决策建议见下方卡片。想改变结论？可以打开对比面板调整权重。");
        response.setDegraded(response.isDegraded() || compare.isDegraded());
    }

    private void handleDetail(String message, Slots slots, String conversationId, String taskId,
                              String userId, ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.DETAIL);
        String productId = slotService.referencedId(message, slots);
        if (productId == null && StringUtils.hasText(slots.getKeyword())) {
            ProductSearchTool.SearchOutcome outcome = searchTool.search(slots, 1);
            if (!outcome.products().isEmpty()) {
                productId = outcome.products().get(0).getProduct().getId();
            }
        }
        if (productId == null) {
            response.setReply("你想了解哪件商品的详情？可以告诉商品名称，或先描述需求让我推荐几款。");
            response.getClarifyOptions().addAll(List.of("帮我推荐蓝牙耳机", "看看跑步鞋", "我的订单"));
            var trace = logService.start(taskId, conversationId, "guide-agent", "clarify",
                    "澄清商品", "无有效商品指代");
            steps.add(trace.success("追问商品"));
            return;
        }
        var detailTrace = logService.start(taskId, conversationId, "guide-agent", "get_product_detail",
                "查询商品详情", productId);
        ProductVO product = productService.detail(productId, userId);
        steps.add(detailTrace.success(product.getName()));
        response.setReply(buildDetailReply(product));
        RecommendedProduct rp = new RecommendedProduct();
        rp.setProduct(product);
        rp.setReason("商品详情");
        rp.setHighlights(new ArrayList<>(product.getAttributes().keySet()).subList(0,
                Math.min(3, product.getAttributes().size())));
        response.getProducts().add(rp);
    }

    private void handleOrder(String message, String conversationId, String taskId, String userId,
                             ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.ORDER);
        var orderTrace = logService.start(taskId, conversationId, "guide-agent", "query_order",
                "查询订单", message);
        Matcher matcher = ORDER_NO.matcher(message);
        if (matcher.find()) {
            Order order = orderService.findByOrderNo(userId, matcher.group());
            if (order != null) {
                response.getOrders().add(orderService.detailForUser(userId, order.getId()));
            }
        } else {
            response.getOrders().addAll(orderService.recentForAgent(userId, 3));
        }
        steps.add(orderTrace.success("查询到 " + response.getOrders().size() + " 个订单"));
        if (response.getOrders().isEmpty()) {
            response.setReply("没有查询到相关订单。你还没有下单，或者订单编号不正确。可以先去商品列表挑选，结算后就能在这里查询物流状态。");
            response.getClarifyOptions().addAll(List.of("帮我推荐蓝牙耳机", "去购物车结算"));
            return;
        }
        OrderVO latest = response.getOrders().get(0);
        StringBuilder reply = new StringBuilder("找到 ").append(response.getOrders().size()).append(" 个订单。");
        reply.append("最近一个订单 ").append(latest.getOrderNo()).append(" 当前状态：")
                .append(latest.getStatusLabel()).append("，共 ").append(latest.getTotalQuantity())
                .append(" 件商品，金额 ").append(latest.getTotalAmount().stripTrailingZeros().toPlainString())
                .append(" 元。");
        if ("PENDING_PAYMENT".equals(latest.getStatus())) {
            reply.append("订单还未支付，请在 30 分钟内完成支付，否则会自动关闭。");
        } else if ("SHIPPED".equals(latest.getStatus())) {
            reply.append("包裹已发出，注意查收。");
        } else if ("COMPLETED".equals(latest.getStatus())) {
            reply.append("订单已完成，感谢你的购买。");
        }
        response.setReply(reply.toString());
    }

    private void handleAddToCart(String message, Slots slots, String conversationId, String taskId,
                                 ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.ADD_TO_CART);
        String productId = slotService.referencedId(message, slots);
        if (productId == null) {
            response.setReply("要加入购物车的是哪件商品？你可以说“把第一款加入购物车”，或直接点击商品卡片上的加购按钮。");
            response.getClarifyOptions().addAll(slots.getLastProductNames());
            var trace = logService.start(taskId, conversationId, "guide-agent", "clarify",
                    "澄清加购商品", "无商品指代");
            steps.add(trace.success("追问商品"));
            return;
        }
        int quantity = 1;
        Matcher quantityMatcher = QUANTITY.matcher(message);
        if (quantityMatcher.find()) {
            try {
                quantity = Math.min(99, Math.max(1, Integer.parseInt(quantityMatcher.group(1))));
            } catch (NumberFormatException ignored) {
            }
        }
        var cartTrace = logService.start(taskId, conversationId, "guide-agent", "add_to_cart",
                "加入购物车", productId + " x " + quantity);
        AddCartRequest cartRequest = new AddCartRequest();
        cartRequest.setProductId(productId);
        cartRequest.setQuantity(quantity);
        cartService.add(cartRequest);
        CartSummaryVO summary = cartService.summary();
        Product product = productMapper.selectById(productId);
        steps.add(cartTrace.success("已加入，购物车共 " + summary.getTotalQuantity() + " 件"));
        response.setReply("已把「" + (product == null ? "商品" : product.getName()) + "」加入购物车，数量 "
                + quantity + "。购物车当前共 " + summary.getTotalQuantity() + " 件商品，合计 "
                + summary.getTotalAmount().stripTrailingZeros().toPlainString() + " 元。需要我帮你一起结算吗？");
        response.getClarifyOptions().addAll(List.of("去购物车结算", "继续推荐", "帮我对比前两个"));
        if (product != null) {
            RecommendedProduct rp = new RecommendedProduct();
            rp.setProduct(productService.toVO(product, productService.categoryMap(), null, null));
            rp.setReason("已加入购物车");
            response.getProducts().add(rp);
        }
    }

    private void handleChitchat(String message, String conversationId, String taskId,
                                ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.CHITCHAT);
        var trace = logService.start(taskId, conversationId, "guide-agent", "chitchat", "闲聊回复", message);
        String reply = null;
        if (llmService.isAvailable()) {
            reply = llmService.complete("""
                    你是「智选商城」的智能导购助手，回答简洁友好，不超过 80 字。
                    你可以帮用户：按需求找商品、对比商品、查订单、加入购物车。
                    不要编造商品和订单信息。
                    """, message);
        }
        if (reply == null) {
            reply = "你好，我是智选商城的智能导购助手。你可以直接描述需求，比如“想买跑步用的蓝牙耳机，预算 300 以内”，"
                    + "我也可以帮你对比商品、查询订单或加入购物车。";
        }
        response.setReply(reply);
        response.getClarifyOptions().addAll(List.of("想买跑步耳机，预算300以内", "帮我对比前两个", "查询我的订单"));
        trace.success("闲聊回复");
    }

    private String buildSearchReply(Slots slots, int count, ProductSearchTool.SearchOutcome outcome) {
        StringBuilder reply = new StringBuilder();
        if (StringUtils.hasText(slots.getCategoryName())) {
            reply.append("按你的需求，为你筛选出 ").append(count).append(" 款")
                    .append(slots.getCategoryName());
        } else {
            reply.append("为你找到 ").append(count).append(" 款相关商品");
        }
        List<String> conditions = new ArrayList<>();
        if (slots.getMaxPrice() != null) {
            conditions.add("预算 " + slots.getMaxPrice().stripTrailingZeros().toPlainString() + " 元以内");
        }
        if (slots.getMinPrice() != null) {
            conditions.add(slots.getMinPrice().stripTrailingZeros().toPlainString() + " 元以上");
        }
        if (!slots.getFeatures().isEmpty()) {
            conditions.add("偏好 " + String.join("、", slots.getFeatures()));
        }
        if (StringUtils.hasText(slots.getScene())) {
            conditions.add(slots.getScene() + "场景");
        }
        if (!conditions.isEmpty()) {
            reply.append("（").append(String.join("，", conditions)).append("）");
        }
        reply.append("，已按匹配度与口碑排序。推荐理由均引用商品库真实参数，价格与库存已校验。");
        if (outcome.relaxed()) {
            reply.append(" 提示：当前预算内选择较少，已为你适当放宽价格条件。");
        }
        return reply.toString();
    }

    private String buildDetailReply(ProductVO product) {
        List<String> attrs = new ArrayList<>();
        product.getAttributes().forEach((key, value) -> attrs.add(key + " " + value));
        String attrText = attrs.isEmpty() ? "暂无更多参数" : String.join("、", attrs);
        return "「" + product.getName() + "」当前售价 " + product.getPrice().stripTrailingZeros().toPlainString()
                + " 元，库存 " + product.getStock() + " 件，综合评分 " + product.getRating() + "。核心参数："
                + attrText + "。如果你想和其他商品对比，可以告诉我商品名称。";
    }

    private Slots parseSlots(String json) {
        Slots slots = JsonUtils.fromJson(json, Slots.class);
        return slots == null ? new Slots() : slots;
    }

    private void copySlots(Slots source, Slots target) {
        target.setCategoryId(source.getCategoryId());
        target.setCategoryName(source.getCategoryName());
        target.setKeyword(source.getKeyword());
        target.setBrand(source.getBrand());
        target.setScene(source.getScene());
        target.setMinPrice(source.getMinPrice());
        target.setMaxPrice(source.getMaxPrice());
        target.setFeatures(new ArrayList<>(source.getFeatures()));
        target.setBudgetUnlimited(source.isBudgetUnlimited());
        target.setAskedBudget(source.isAskedBudget());
        target.setPending(source.getPending());
        target.setLastProductIds(new ArrayList<>(source.getLastProductIds()));
        target.setLastProductNames(new ArrayList<>(source.getLastProductNames()));
    }

    private String describeSlots(Slots slots) {
        List<String> parts = new ArrayList<>();
        if (slots.getCategoryName() != null) {
            parts.add("品类=" + slots.getCategoryName());
        }
        if (slots.getMaxPrice() != null) {
            parts.add("预算≤" + slots.getMaxPrice().stripTrailingZeros().toPlainString());
        }
        if (!slots.getFeatures().isEmpty()) {
            parts.add("偏好=" + String.join("/", slots.getFeatures()));
        }
        if (slots.getScene() != null) {
            parts.add("场景=" + slots.getScene());
        }
        return parts.isEmpty() ? "无" : String.join("，", parts);
    }

    private List<String> categoryOptions() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1).isNotNull(Category::getParentId)
                        .orderByAsc(Category::getSortOrder).last("LIMIT 6"))
                .stream().map(Category::getName).toList();
    }
}
