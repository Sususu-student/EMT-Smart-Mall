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
    private final PromptService promptService;

    private static final Pattern COMPARE = Pattern.compile("对比|比较|哪个好|哪款好|选哪个|怎么选|差别|区别|vs|VS");
    private static final Pattern ORDER = Pattern.compile("订单|物流|发货|到货|快递|签收|退款|售后|买的东西|支付状态");
    private static final Pattern CART = Pattern.compile("加入购物车|加购|加到购物车|放进购物车|购物车|买这个|就要这个|下单");
    private static final Pattern DETAIL = Pattern.compile("详情|参数|配置|怎么样|好用吗|续航多久|多少钱|什么价格|介绍一下|看看|具体|值得买吗|优缺点");
    private static final Pattern STRONG_DETAIL = Pattern.compile("详情|参数|配置|续航多久|多少钱|什么价格|库存|值得买吗|优缺点|介绍一下|具体");
    private static final Pattern PRODUCT_REFERENCE = Pattern.compile("这款|这个|它|该商品|第一个|第二个|第三个|第[一二三四]款|p_[a-z_]+_\\d{2}");
    private static final Pattern CHITCHAT = Pattern.compile("^(你好|您好|hi|hello|嗨|在吗|谢谢(你|您)?|感谢(你|您)?|多谢|再见|拜拜|你是谁|你能做什么|怎么用|help)[!！。,.，\\s]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GREETING_PREFIX = Pattern.compile("^(你好|您好|hi|hello|嗨)[!！。,.，\\s]");

    public IntentResult detect(String message, List<String> context) {
        String text = message == null ? "" : message.trim();
        IntentResult rule = ruleDetect(text);
        if (!llmService.isAvailable()) {
            return rule;
        }
        String system = promptService.text("intent.system");
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
            boolean browseCategory = (text.contains("看看") || text.contains("看一下"))
                    && !STRONG_DETAIL.matcher(text).find()
                    && !PRODUCT_REFERENCE.matcher(text).find();
            if (browseCategory) {
                return IntentResult.of(Intents.SEARCH, 0.8, "rule", "浏览品类，按商品检索处理");
            }
            return IntentResult.of(Intents.DETAIL, 0.8, "rule", "命中详情询问关键词");
        }
        return IntentResult.of(Intents.SEARCH, 0.7, "rule", "默认按商品检索处理");
    }
}
