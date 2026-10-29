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
import com.smartmall.order.dto.CreateOrderRequest;
import com.smartmall.order.dto.OrderVO;
import com.smartmall.order.entity.Order;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.service.ProductService;
import com.smartmall.user.dto.PreferenceVO;
import com.smartmall.user.entity.UserAddress;
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
    private final ScenePlannerService scenePlannerService;
    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;
    private final AgentLogService logService;
    private final LlmService llmService;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final AgentWebSocketHandler webSocketHandler;
    private final PromptService promptService;

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
                case Intents.SCENE_PLAN -> handleScenePlan(message, slots, conversation.getId(), taskId, response, steps);
                case Intents.ORDER -> handleOrder(message, conversation.getId(), taskId, userId, response, steps);
                case Intents.ADD_TO_CART -> handleAddToCart(message, slots, conversation.getId(), taskId, response, steps);
                case Intents.CHECKOUT -> handleCheckout(message, slots, conversation.getId(), taskId, response, steps);
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
        response.setSlots(slots);
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
        applyPreferences(message, slots, userId, response);
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

/**
     * 场景搭配：识别场景 + 抽取预算 → 组合整套商品 → 由 LLM 生成搭配理由。
     * 与单件推荐的区别在于输出是一整个方案，而不是一堆候选。
     */
    private void handleScenePlan(String message, Slots slots, String conversationId, String taskId,
                                 ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.SCENE_PLAN);

        var sceneTrace = logService.start(taskId, conversationId, "guide-agent", "detect_scene",
                "识别使用场景", message);
        ScenePlannerService.Scene scene = scenePlannerService.detectScene(message);
        if (scene == null) {
            steps.add(sceneTrace.failed("未识别到具体场景"));
            response.setReply("我可以帮你做场景搭配。告诉我场景和预算就行，比如「明天去海边度假，预算 1000」或者「在家健身买点什么」。");
            response.getClarifyOptions().addAll(List.of(
                    "明天去海边度假，预算 1000",
                    "通勤办公配一套，预算 800",
                    "在家健身装备，预算 500"));
            return;
        }
        steps.add(sceneTrace.success("场景：" + scene.getName()));

        // 预算：优先取自然语言里的金额，没有就用默认档位
        BigDecimal budget = extractBudget(message);
        var planTrace = logService.start(taskId, conversationId, "guide-agent", "plan_scene",
                "生成搭配方案", scene.getName() + "，预算 " + budget);
        ScenePlannerService.Plan plan = scenePlannerService.plan(scene, budget);
        steps.add(planTrace.success("搭配 " + plan.getItems().size() + " 件，合计 ¥" + plan.getTotal()));

        if (plan.getItems().isEmpty()) {
            response.setReply("这个预算下暂时没找到合适的商品组合，可以稍微放宽预算再试试。");
            return;
        }

        // 搭配理由交给 LLM 生成，降级时用模板兜底
        String reason = null;
        if (llmService.isAvailable()) {
            var reasonTrace = logService.start(taskId, conversationId, "guide-agent", "explain_plan",
                    "生成搭配理由", "让模型解释为什么这样搭");
            try {
                String prompt = "你是一位贴心的导购。用户要去「" + scene.getName() + "」，预算 ¥" + plan.getBudget()
                        + "。\n已为你选好以下商品：\n"
                        + scenePlannerService.describe(plan)
                        + "\n请用 2-3 句话解释为什么这样搭配，语气自然、有画面感，不要罗列参数，不要重复价格明细。";
                reason = llmService.completeFast(promptService.text("chitchat.system"), prompt);
                steps.add(reasonTrace.success("已生成搭配说明"));
            } catch (Exception e) {
                steps.add(reasonTrace.failed("模型生成失败，使用模板文案"));
            }
        }
        if (reason == null || reason.isBlank()) {
            reason = "按「" + scene.getName() + "」的典型需求搭配了 " + plan.getItems().size()
                    + " 件商品，覆盖了主要使用环节，合计 ¥" + plan.getTotal() + "，在预算之内。";
        }

        response.setScenePlan(buildScenePlanVO(plan, reason));
        response.setReply("已为你准备一套「" + scene.getName() + "」方案，共 " + plan.getItems().size()
                + " 件，合计 ¥" + plan.getTotal() + "。可以直接一键加购，也可以让我换一套。");
    }

    /** 从用户原话中提取预算金额，支持「预算1000」「1000以内」「不超过800」等说法 */
    private BigDecimal extractBudget(String message) {
        if (message == null) {
            return new BigDecimal("1000");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{2,6})\\s*(?:元|块|以内|以下)?").matcher(message);
        BigDecimal best = null;
        while (m.find()) {
            try {
                BigDecimal v = new BigDecimal(m.group(1));
                // 忽略像年份、数量这类明显不是预算的数字
                if (v.compareTo(new BigDecimal("50")) >= 0 && v.compareTo(new BigDecimal("100000")) <= 0) {
                    best = best == null ? v : best.max(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return best == null ? new BigDecimal("1000") : best;
    }

    /** 把内部 Plan 转成前端渲染用的 VO */
    private com.smartmall.agent.dto.ScenePlanVO buildScenePlanVO(ScenePlannerService.Plan plan, String reason) {
        com.smartmall.agent.dto.ScenePlanVO vo = new com.smartmall.agent.dto.ScenePlanVO();
        vo.setSceneKey(plan.getSceneKey());
        vo.setSceneName(plan.getSceneName());
        vo.setBudget(plan.getBudget());
        vo.setTotal(plan.getTotal());
        vo.setWithinBudget(plan.isWithinBudget());
        vo.setReason(reason);
        List<com.smartmall.agent.dto.ScenePlanVO.PlanItemVO> items = new ArrayList<>();
        for (ScenePlannerService.PlanItem item : plan.getItems()) {
            com.smartmall.agent.dto.ScenePlanVO.PlanItemVO vo2 = new com.smartmall.agent.dto.ScenePlanVO.PlanItemVO();
            vo2.setProductId(item.getProductId());
            vo2.setName(item.getName());
            vo2.setImage(item.getImage());
            vo2.setImageFallback(item.getImageFallback());
            vo2.setPrice(item.getPrice());
            vo2.setRole(item.getRole());
            vo2.setRating(item.getRating());
            items.add(vo2);
        }
        vo.setItems(items);
        return vo;
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
        if (message.contains("政策") || message.contains("怎么退") || message.contains("售后怎么")) {
            var policyTrace = logService.start(taskId, conversationId, "guide-agent", "after_sale_policy",
                    "售后政策解答", message);
            response.setReply("本演示支持签收后 7 天内申请退款、退货退款或换货：在订单详情页点击“申请售后”，"
                    + "填写类型和原因，管理员审核后会给出处理备注。演示环境不产生真实退款。");
            response.getClarifyOptions().addAll(List.of("查看我的订单", "我要申请售后"));
            steps.add(policyTrace.success("返回售后政策"));
            return;
        }
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
            reply.append("包裹已发出");
            if (StringUtils.hasText(latest.getCarrier())) {
                reply.append("，承运商 ").append(latest.getCarrier());
            }
            if (StringUtils.hasText(latest.getTrackingNo())) {
                reply.append("，运单号 ").append(latest.getTrackingNo());
            }
            reply.append("，注意查收。");
        } else if ("COMPLETED".equals(latest.getStatus())) {
            reply.append("订单已完成，感谢你的购买。");
        }
        response.setReply(reply.toString());
    }

    private void handleAddToCart(String message, Slots slots, String conversationId, String taskId,
                                 ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.ADD_TO_CART);
        boolean queryCart = (message.contains("有什么") || message.contains("查看") || message.contains("看看")
                || message.contains("清单") || message.trim().equals("购物车"))
                && !message.contains("加入") && !message.contains("加购") && !message.contains("放进");
        if (queryCart) {
            var cartTrace = logService.start(taskId, conversationId, "guide-agent", "query_cart",
                    "查询购物车", message);
            CartSummaryVO summary = cartService.summary();
            if (summary.getItems().isEmpty()) {
                response.setReply("你的购物车还是空的。可以告诉我想买什么，我先帮你推荐几款。");
            } else {
                String items = summary.getItems().stream().limit(5)
                        .map(item -> "「" + item.getName() + "」×" + item.getQuantity())
                        .collect(java.util.stream.Collectors.joining("、"));
                response.setReply("你的购物车里有 " + items + "，共 " + summary.getTotalQuantity()
                        + " 件，合计 " + summary.getTotalAmount().stripTrailingZeros().toPlainString()
                        + " 元。需要我帮你结算吗？");
            }
            response.getClarifyOptions().addAll(List.of("去购物车结算", "继续推荐"));
            steps.add(cartTrace.success("购物车共 " + summary.getTotalQuantity() + " 件"));
            return;
        }
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

    private void handleCheckout(String message, Slots slots, String conversationId, String taskId,
                                ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.CHECKOUT);
        if (slots.isPendingCheckout() && (message.contains("取消") || message.contains("算了"))) {
            slots.setPendingCheckout(false);
            response.setReply("已取消本次下单。购物车商品会保留，你可以继续挑选或直接去购物车结算。");
            response.getClarifyOptions().addAll(List.of("继续推荐", "去购物车结算"));
            steps.add(logService.start(taskId, conversationId, "guide-agent", "cancel_checkout",
                    "取消下单", message).success("已取消待确认订单"));
            return;
        }

        CartSummaryVO summary = cartService.summary();
        List<com.smartmall.cart.dto.CartItemVO> selected = summary.getItems().stream()
                .filter(item -> item.getSelected() != null && item.getSelected() == 1 && item.getAvailable())
                .toList();
        if (selected.isEmpty()) {
            slots.setPendingCheckout(false);
            response.setReply("购物车里还没有勾选商品。可以先去购物车勾选，或告诉我想买什么，我先帮你推荐。");
            response.getClarifyOptions().addAll(List.of("去购物车", "继续推荐"));
            steps.add(logService.start(taskId, conversationId, "guide-agent", "checkout_preview",
                    "下单前检查", "购物车无选中商品").failed("没有可结算商品"));
            return;
        }
        List<UserAddress> addresses = userService.addresses();
        if (addresses.isEmpty()) {
            slots.setPendingCheckout(false);
            response.setReply("你还没有收货地址。请先在个人中心添加地址，再回来让我协助下单。");
            response.getClarifyOptions().addAll(List.of("去个人中心", "查看购物车"));
            steps.add(logService.start(taskId, conversationId, "guide-agent", "checkout_preview",
                    "下单前检查", "无收货地址").failed("缺少收货地址"));
            return;
        }
        UserAddress address = addresses.stream().filter(item -> item.getIsDefault() != null && item.getIsDefault() == 1)
                .findFirst().orElse(addresses.get(0));

        String method = (message.contains("加急") || message.contains("顺丰") || message.contains("尽快"))
                ? "EXPRESS" : (slots.getDeliveryMethod() == null ? "STANDARD" : slots.getDeliveryMethod());
        slots.setDeliveryMethod(method);
        BigDecimal fee = "EXPRESS".equals(method) ? BigDecimal.valueOf(12) : BigDecimal.ZERO;
        BigDecimal payable = summary.getTotalAmount().add(fee);
        boolean confirm = message.contains("确认") || message.contains("提交") || message.contains("就这个")
                || message.contains("可以下单") || message.contains("下单吧");
        if (!slots.isPendingCheckout() || !confirm) {
            slots.setPendingCheckout(true);
            response.setReply("已为你准备订单：" + selected.size() + " 种商品，共 " + summary.getTotalQuantity()
                    + " 件，商品金额 " + summary.getTotalAmount().stripTrailingZeros().toPlainString()
                    + " 元，配送方式 " + ("EXPRESS".equals(method) ? "加急配送（12 元）" : "标准配送（免运费）")
                    + "，应付 " + payable.stripTrailingZeros().toPlainString() + " 元。收货地址：" + address.getReceiver()
                    + "，" + address.fullAddress() + "。回复「确认下单」后我会创建订单，支付仍在订单详情完成。");
            response.getClarifyOptions().addAll(List.of("确认下单", "取消"));
            steps.add(logService.start(taskId, conversationId, "guide-agent", "checkout_preview",
                    "下单预览", "待确认").success("已生成订单预览，等待用户确认"));
            return;
        }

        var createTrace = logService.start(taskId, conversationId, "guide-agent", "create_order",
                "协助创建订单", "地址 " + address.getId() + "，配送 " + method);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(address.getId());
        request.setDeliveryMethod(method);
        request.setRemark("由导购 Agent 协助创建");
        OrderVO order = orderService.create(request);
        slots.setPendingCheckout(false);
        response.getOrders().add(order);
        response.setReply("订单已创建：" + order.getOrderNo() + "，共 " + order.getTotalQuantity() + " 件，应付 "
                + order.getTotalAmount().stripTrailingZeros().toPlainString()
                + " 元。请点击订单卡片去订单详情完成支付。");
        response.getClarifyOptions().addAll(List.of("去支付", "查看订单"));
        steps.add(createTrace.success("订单 " + order.getOrderNo() + " 已创建"));
    }

    private void handleChitchat(String message, String conversationId, String taskId,
                                ChatResponse response, List<AgentStep> steps) {
        response.setIntent(Intents.CHITCHAT);
        var trace = logService.start(taskId, conversationId, "guide-agent", "chitchat", "闲聊回复", message);
        String reply = null;
        if (llmService.isAvailable()) {
            reply = llmService.completeFast(promptService.text("chitchat.system"), message);
        }
        if (reply == null) {
            reply = "你好，我是智选商城的智能导购助手。你可以直接描述需求，比如“想买跑步用的蓝牙耳机，预算 300 以内”，"
                    + "我也可以帮你对比商品、查询订单或加入购物车。";
        }
        response.setReply(reply);
        response.getClarifyOptions().addAll(List.of("想买跑步耳机，预算300以内", "帮我对比前两个", "查询我的订单"));
        trace.success("闲聊回复");
    }

    /**
     * 用户偏好只做“软补充”：仅当品类与偏好品类一致时补场景/品牌；
     * 模糊推荐时可以用偏好品类起手。预算不自动套用，避免跳过澄清。
     */
    private void applyPreferences(String message, Slots slots, String userId, ChatResponse response) {
        List<PreferenceVO> preferences;
        try {
            preferences = userService.preferences();
        } catch (Exception e) {
            return;
        }
        if (preferences.isEmpty()) {
            return;
        }
        String category = preference(preferences, "品类");
        String scene = preference(preferences, "场景");
        String brand = preference(preferences, "品牌");
        if (category != null && !slots.hasCategory() && !StringUtils.hasText(slots.getKeyword())
                && (message.contains("推荐") || message.contains("随便") || message.contains("看着办"))) {
            Category leaf = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, category).eq(Category::getStatus, 1).last("LIMIT 1"));
            if (leaf != null) {
                slots.setCategoryId(leaf.getId());
                slots.setCategoryName(leaf.getName());
                response.getAppliedPreferences().add("品类：" + leaf.getName());
            }
        }
        if (category == null || !category.equals(slots.getCategoryName())) {
            return;
        }
        if (scene != null && slots.getScene() == null) {
            slots.setScene(scene);
            response.getAppliedPreferences().add("场景：" + scene);
        }
        if (brand != null && slots.getBrand() == null) {
            slots.setBrand(brand);
            response.getAppliedPreferences().add("品牌：" + brand);
        }
    }

    private String preference(List<PreferenceVO> preferences, String key) {
        return preferences.stream().filter(item -> key.equals(item.getKey()))
                .map(PreferenceVO::getValue).filter(StringUtils::hasText).findFirst().orElse(null);
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
        target.setPendingCheckout(source.isPendingCheckout());
        target.setDeliveryMethod(source.getDeliveryMethod());
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
