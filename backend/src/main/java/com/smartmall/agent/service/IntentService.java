package com.smartmall.agent.service;

import com.smartmall.agent.Intents;
import com.smartmall.agent.dto.IntentResult;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.common.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 意图识别：LLM 分类为主，强规则兜底/纠偏。
 * LLM 不可用时完全由规则引擎工作，保证演示与基础功能不中断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final LlmService llmService;

    private static final Pattern COMPARE = Pattern.compile("对比|比较|哪个好|哪款好|选哪个|怎么选|差别|区别|vs|VS");
    private static final Pattern ORDER = Pattern.compile("订单|物流|发货|到货|快递|签收|退款|售后|买的东西|支付状态");
    private static final Pattern CART = Pattern.compile("加入购物车|加购|加到购物车|放进购物车|购物车|买这个|就要这个|下单");
    private static final Pattern DETAIL = Pattern.compile("详情|参数|配置|怎么样|好用吗|续航多久|多少钱|什么价格|介绍一下|看看|具体|值得买吗|优缺点");
    private static final Pattern CHITCHAT = Pattern.compile("^(你好|您好|hi|hello|嗨|在吗|谢谢|感谢|再见|拜拜|你是谁|你能做什么|怎么用|help)[!！。,.，\\s]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GREETING_PREFIX = Pattern.compile("^(你好|您好|hi|hello|嗨)[!！。,.，\\s]");

    public IntentResult detect(String message, List<String> context) {
        String text = message == null ? "" : message.trim();
        IntentResult rule = ruleDetect(text);
        if (!llmService.isAvailable()) {
            return rule;
        }
        String system = """
                你是电商智能导购系统的意图分类器。只输出一个 JSON 对象，不要输出 Markdown。
                可选意图：
                - search_product: 用户想找/买某类商品，描述需求、预算、场景
                - compare: 用户要求对比两个或多个商品
                - ask_detail: 用户询问某个已推荐商品的具体参数或细节
                - query_order: 用户询问订单、物流、发货状态
                - add_to_cart: 用户要求把某个商品加入购物车
                - chitchat: 打招呼、感谢、询问助手能力等
                只输出: {"intent":"search_product","confidence":0.0-1.0}
                """;
        String user = "对话上下文：\n" + String.join("\n", context) + "\n\n当前用户消息：" + text;
        String answer = llmService.complete(system, user);
        if (answer == null) {
            return rule;
        }
        Map<String, Object> json = JsonUtils.toMap(JsonUtils.extractJson(answer));
        String intent = String.valueOf(json.getOrDefault("intent", ""));
        if (!Intents.valid(intent)) {
            return rule;
        }
        double confidence = 0.7;
        try {
            confidence = Double.parseDouble(String.valueOf(json.getOrDefault("confidence", 0.7)));
        } catch (NumberFormatException ignored) {
        }
        // 强规则信号优先，避免 LLM 把“对比/订单”误判成检索
        if (!Intents.SEARCH.equals(rule.getIntent()) && rule.getConfidence() >= 0.85
                && !rule.getIntent().equals(intent)) {
            return rule;
        }
        return IntentResult.of(intent, confidence, "llm", "大模型分类");
    }

    public IntentResult ruleDetect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (COMPARE.matcher(text).find()) {
            return IntentResult.of(Intents.COMPARE, 0.95, "rule", "命中对比关键词");
        }
        if (ORDER.matcher(text).find()) {
            return IntentResult.of(Intents.ORDER, 0.95, "rule", "命中订单/物流关键词");
        }
        if (CART.matcher(text).find()) {
            return IntentResult.of(Intents.ADD_TO_CART, 0.9, "rule", "命中加购关键词");
        }
        if (CHITCHAT.matcher(lower).matches() || GREETING_PREFIX.matcher(lower).find() && text.length() <= 12) {
            return IntentResult.of(Intents.CHITCHAT, 0.95, "rule", "命中寒暄/能力询问");
        }
        if (DETAIL.matcher(text).find()) {
            return IntentResult.of(Intents.DETAIL, 0.8, "rule", "命中详情询问关键词");
        }
        return IntentResult.of(Intents.SEARCH, 0.7, "rule", "默认按商品检索处理");
    }
}
