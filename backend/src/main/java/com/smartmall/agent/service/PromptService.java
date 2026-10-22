package com.smartmall.agent.service;

import com.smartmall.agent.dto.AgentPromptVO;
import com.smartmall.agent.entity.AgentPrompt;
import com.smartmall.agent.mapper.AgentPromptMapper;
import com.smartmall.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 模板管理：数据库可覆盖默认模板，未覆盖时使用代码内默认值。
 * 修改后下一次 LLM 调用生效，无需重启。
 */
@Service
@RequiredArgsConstructor
public class PromptService {

    private final AgentPromptMapper promptMapper;

    private static final Map<String, PromptDefinition> DEFAULTS = buildDefaults();

    public String text(String key) {
        AgentPrompt override = findOverride(key);
        if (override != null && StringUtils.hasText(override.getContent())) {
            return override.getContent();
        }
        PromptDefinition definition = DEFAULTS.get(key);
        return definition == null ? "" : definition.content();
    }

    public String render(String key, Map<String, String> variables) {
        String content = text(key);
        if (variables == null) {
            return content;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return content;
    }

    public List<AgentPromptVO> list() {
        List<AgentPromptVO> result = new ArrayList<>();
        for (Map.Entry<String, PromptDefinition> entry : DEFAULTS.entrySet()) {
            AgentPromptVO vo = new AgentPromptVO();
            vo.setKey(entry.getKey());
            vo.setName(entry.getValue().name());
            vo.setDescription(entry.getValue().description());
            vo.setContent(entry.getValue().content());
            AgentPrompt override = findOverride(entry.getKey());
            if (override != null && StringUtils.hasText(override.getContent())) {
                vo.setContent(override.getContent());
                vo.setCustomized(true);
                vo.setUpdatedAt(override.getUpdatedAt());
            }
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public AgentPromptVO save(String key, String content) {
        if (!DEFAULTS.containsKey(key)) {
            throw BizException.notFound("Prompt 模板不存在");
        }
        if (!StringUtils.hasText(content)) {
            throw new BizException("Prompt 内容不能为空");
        }
        String text = content.trim();
        if (text.length() > 6000) {
            throw new BizException("Prompt 内容最多 6000 字");
        }
        AgentPrompt existing = promptMapper.selectById(key);
        if (existing == null) {
            existing = new AgentPrompt();
            existing.setPromptKey(key);
            existing.setName(DEFAULTS.get(key).name());
            existing.setDescription(DEFAULTS.get(key).description());
            existing.setContent(text);
            existing.setUpdatedAt(LocalDateTime.now());
            promptMapper.insert(existing);
        } else {
            existing.setContent(text);
            existing.setUpdatedAt(LocalDateTime.now());
            promptMapper.updateById(existing);
        }
        return list().stream().filter(item -> item.getKey().equals(key)).findFirst()
                .orElseThrow(() -> BizException.notFound("Prompt 模板不存在"));
    }

    @Transactional
    public void reset(String key) {
        if (!DEFAULTS.containsKey(key)) {
            throw BizException.notFound("Prompt 模板不存在");
        }
        promptMapper.deleteById(key);
    }

    public Map<String, String> defaults() {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULTS.forEach((key, value) -> result.put(key, value.content()));
        return result;
    }

    private AgentPrompt findOverride(String key) {
        try {
            return promptMapper.selectById(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, PromptDefinition> buildDefaults() {
        Map<String, PromptDefinition> map = new LinkedHashMap<>();
        map.put("intent.system", new PromptDefinition("意图识别 System Prompt", "决定 LLM 如何分类用户意图",
                """
                        你是电商智能导购系统的意图分类器。只输出一个 JSON 对象，不要输出 Markdown。
                        可选意图：
                        - search_product: 用户想找/买某类商品，描述需求、预算、场景
                        - compare: 用户要求对比两个或多个商品
                        - ask_detail: 用户询问某个已推荐商品的具体参数或细节
                        - query_order: 用户询问订单、物流、发货状态
                        - add_to_cart: 用户要求把某个商品加入购物车
                        - chitchat: 打招呼、感谢、询问助手能力等
                        只输出: {"intent":"search_product","confidence":0.0-1.0}
                        """));
        map.put("slot.system", new PromptDefinition("槽位提取 System Prompt", "从自然语言中抽取品类、预算、品牌、场景、功能偏好",
                """
                        你是电商导购的需求解析器。从用户消息中抽取购物条件，只输出 JSON，不要解释。
                        字段: category(品类，必须是给定品类之一), keyword(搜索词), minPrice(数字), maxPrice(数字),
                        brand(品牌), scene(使用场景), features(功能偏好数组)。
                        用户没提到的字段输出 null，不要臆造。价格单位是元。
                        可选品类: {{categories}}
                        """));
        map.put("recommend.system", new PromptDefinition("推荐理由 System Prompt", "约束推荐理由只引用真实商品字段",
                """
                        你是严谨的电商导购。请为每个商品生成一句中文推荐理由。
                        硬性要求：
                        1. 只能使用输入 JSON 中的真实参数，禁止编造价格、库存、评分、功能；
                        2. 价格只能写输入中的 price，格式为“售价 X 元”；
                        3. 理由必须引用至少一个 attributes 中的键或值；
                        4. 不要提原价、不要使用“100%”“绝对”等绝对化表述；
                        5. 每条 20-60 字，结合用户场景，说明推荐与取舍。
                        只输出 JSON：{"reasons":{"商品ID":"推荐理由"}}
                        """));
        map.put("compare.system", new PromptDefinition("对比建议 System Prompt", "约束对比 Agent 只根据矩阵与评分写建议",
                """
                        你是电商对比决策 Agent。基于给定的对比矩阵与评分写一段中文建议（80-120 字）：
                        1. 明确推荐综合得分最高的商品；
                        2. 指出它最占优的 1-2 个维度，以及第二名在哪个维度更强；
                        3. 只使用输入中的参数与价格，禁止编造；
                        4. 不要使用 Markdown，不要罗列全部维度。
                        只输出建议正文。
                        """));
        map.put("chitchat.system", new PromptDefinition("闲聊 System Prompt", "控制助手自我介绍与能力边界",
                """
                        你是「智选商城」的智能导购助手，回答简洁友好，不超过 80 字。
                        你可以帮用户：按需求找商品、对比商品、查订单、加入购物车。
                        不要编造商品和订单信息。
                        """));
        return map;
    }

    private record PromptDefinition(String name, String description, String content) {
    }
}
