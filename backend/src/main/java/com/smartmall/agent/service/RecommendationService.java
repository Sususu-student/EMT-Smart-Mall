package com.smartmall.agent.service;

import com.smartmall.agent.dto.RecommendedProduct;
import com.smartmall.agent.dto.ScoredProduct;
import com.smartmall.agent.dto.Slots;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.common.JsonUtils;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final LlmService llmService;
    private final ProductService productService;
    private final FieldValidator fieldValidator;
    private final PromptService promptService;

    public Result generate(List<ScoredProduct> scored, Slots slots) {
        List<String> issues = new ArrayList<>();
        Map<String, String> llmReasons = new LinkedHashMap<>();
        boolean llmUsed = false;

        if (llmService.isAvailable() && !scored.isEmpty()) {
            try {
                llmReasons = askLlm(scored, slots);
                llmUsed = !llmReasons.isEmpty();
            } catch (Exception e) {
                issues.add("LLM 推荐理由生成失败：" + e.getMessage());
            }
        }

        Map<String, Category> categories = productService.categoryMap();
        List<RecommendedProduct> items = new ArrayList<>();
        for (ScoredProduct sp : scored) {
            Product product = sp.getProduct();
            String reason = llmReasons.get(product.getId());
            boolean fallback = false;
            if (reason != null) {
                FieldValidator.ValidationResult validation = fieldValidator.validate(product, reason);
                if (!validation.isValid()) {
                    issues.add(product.getId() + " 推荐理由被拦截：" + String.join("；", validation.getIssues()));
                    reason = null;
                }
            }
            if (reason == null) {
                reason = templateReason(product, slots, categories);
                fallback = true;
            }
            RecommendedProduct item = new RecommendedProduct();
            item.setProduct(toVO(product, categories));
            item.setReason(reason);
            item.setScore(Math.round(sp.getScore() * 10) / 10.0);
            item.setReasonFallback(fallback);
            item.setHighlights(highlights(product, sp, slots));
            items.add(item);
        }
        return new Result(items, llmUsed, issues);
    }

    private Map<String, String> askLlm(List<ScoredProduct> scored, Slots slots) {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (ScoredProduct sp : scored) {
            Product p = sp.getProduct();
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("id", p.getId());
            fact.put("name", p.getName());
            fact.put("price", p.getPrice());
            fact.put("stock", p.getStock());
            fact.put("rating", p.getRating());
            fact.put("attributes", JsonUtils.toMap(p.getAttributes()));
            facts.add(fact);
        }
        String system = promptService.text("recommend.system");
        String user = "用户需求: " + JsonUtils.toJson(slots)
                + "\n商品事实: " + JsonUtils.toJson(facts);
        String answer = llmService.complete(system, user);
        if (answer == null) {
            return Map.of();
        }
        Map<String, Object> json = JsonUtils.toMap(JsonUtils.extractJson(answer));
        Object reasons = json.get("reasons");
        if (!(reasons instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
        return result;
    }

    private String templateReason(Product product, Slots slots, Map<String, Category> categories) {
        List<String> parts = new ArrayList<>();
        List<Map<String, Object>> schema = schemaOf(product, categories);
        Map<String, Object> attributes = JsonUtils.toMap(product.getAttributes());
        List<String> preferred = new ArrayList<>();
        for (String feature : slots.getFeatures()) {
            attributes.keySet().stream()
                    .filter(key -> key.contains(feature) || feature.contains(key)
                            || (feature.equals("轻量") && key.equals("重量"))
                            || (feature.equals("静音") && key.contains("噪音")))
                    .findFirst().ifPresent(preferred::add);
        }
        for (String key : attributes.keySet()) {
            if (!preferred.contains(key)) {
                preferred.add(key);
            }
        }
        for (String key : preferred) {
            if (parts.size() >= 2) {
                break;
            }
            String text = formatAttribute(key, attributes.get(key), schema);
            if (text != null) {
                parts.add(text);
            }
        }
        String attrText = parts.isEmpty()
                ? (product.getSubtitle() == null ? "商品参数完整" : product.getSubtitle())
                : String.join("、", parts);
        String fit;
        if (slots.getScene() != null && !slots.getScene().isBlank()) {
            fit = "更贴合" + slots.getScene() + "场景";
        } else if (!slots.getFeatures().isEmpty()) {
            fit = "覆盖" + String.join("、", slots.getFeatures()) + "需求";
        } else {
            fit = "参数与价格比较均衡";
        }
        return "售价 %s 元，%s，%s。".formatted(priceText(product.getPrice()), attrText, fit);
    }

    private String formatAttribute(String key, Object value, List<Map<String, Object>> schema) {
        if (value == null) {
            return null;
        }
        String unit = "";
        for (Map<String, Object> meta : schema) {
            if (key.equals(meta.get("key"))) {
                unit = meta.get("unit") == null ? "" : String.valueOf(meta.get("unit"));
                break;
            }
        }
        String text = valueText(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        return key + " " + text + unit;
    }

    private String valueText(Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private String priceText(BigDecimal price) {
        return price == null ? "-" : price.stripTrailingZeros().toPlainString();
    }

    private List<String> highlights(Product product, ScoredProduct sp, Slots slots) {
        List<String> highlights = new ArrayList<>(sp.getHighlights());
        Map<String, Object> attributes = JsonUtils.toMap(product.getAttributes());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (highlights.size() >= 4) {
                break;
            }
            if (slots.getFeatures().stream().anyMatch(f -> entry.getKey().contains(f))
                    && !highlights.contains(entry.getKey())) {
                highlights.add(entry.getKey() + " " + valueText(entry.getValue()));
            }
        }
        if (product.getRating() != null && highlights.size() < 4) {
            highlights.add(product.getRating().stripTrailingZeros().toPlainString() + " 分");
        }
        return highlights;
    }

    private ProductVO toVO(Product product, Map<String, Category> categories) {
        return productService.toVO(product, categories, null, null);
    }

    private List<Map<String, Object>> schemaOf(Product product, Map<String, Category> categories) {
        Category category = categories.get(product.getCategoryId());
        return category == null ? List.of() : JsonUtils.toMapList(category.getAttributeSchema());
    }

    public record Result(List<RecommendedProduct> items, boolean llmUsed, List<String> issues) {
    }
}
