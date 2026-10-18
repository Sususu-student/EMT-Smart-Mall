package com.smartmall.agent.service;

import com.smartmall.common.JsonUtils;
import com.smartmall.product.entity.Product;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字段校验 / 幻觉拦截：
 * 1. 推荐理由中出现的价格必须与数据库一致；
 * 2. 库存数字必须与数据库一致；
 * 3. 理由必须至少引用一个真实商品属性；
 * 4. 禁止绝对化承诺。
 */
@Service
public class FieldValidator {

    private static final Pattern PRICE_CLAIM = Pattern.compile(
            "(?:售价|价格|到手价|仅需|只要|￥|¥)\\s*(?:为|是|：|:)?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern STOCK_CLAIM = Pattern.compile(
            "库存\\s*(?:仅剩|剩余|还有|为|：|:)?\\s*(\\d+)");
    private static final List<String> FORBIDDEN = List.of("100%", "绝对", "保证有效", "永不");

    public ValidationResult validate(Product product, String reason) {
        List<String> issues = new ArrayList<>();
        if (reason == null || reason.isBlank()) {
            return new ValidationResult(false, List.of("推荐理由为空"), List.of());
        }
        String text = reason.trim();
        if (text.length() < 8) {
            issues.add("推荐理由过短");
        }
        if (text.length() > 160) {
            issues.add("推荐理由过长");
        }

        Matcher priceMatcher = PRICE_CLAIM.matcher(text);
        while (priceMatcher.find()) {
            BigDecimal claimed = new BigDecimal(priceMatcher.group(1));
            if (product.getPrice() == null || claimed.compareTo(product.getPrice()) != 0) {
                issues.add("价格不一致：理由为 " + claimed + " 元，数据库为 " + product.getPrice() + " 元");
            }
        }
        Matcher stockMatcher = STOCK_CLAIM.matcher(text);
        while (stockMatcher.find()) {
            int claimed = Integer.parseInt(stockMatcher.group(1));
            if (product.getStock() == null || claimed != product.getStock()) {
                issues.add("库存不一致：理由为 " + claimed + "，数据库为 " + product.getStock());
            }
        }
        for (String forbidden : FORBIDDEN) {
            if (text.contains(forbidden)) {
                issues.add("包含绝对化表述：" + forbidden);
            }
        }
        if (!groundedInAttributes(product, text)) {
            issues.add("理由未引用任何真实商品属性");
        }
        return new ValidationResult(issues.isEmpty(), issues, List.of("price", "stock", "attributes"));
    }

    private boolean groundedInAttributes(Product product, String text) {
        Map<String, Object> attributes = JsonUtils.toMap(product.getAttributes());
        if (attributes.isEmpty()) {
            return text.contains("元");
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (text.contains(entry.getKey())) {
                return true;
            }
            String value = String.valueOf(entry.getValue());
            if (value.length() >= 2 && text.contains(value)) {
                return true;
            }
        }
        return product.getName() != null && text.contains(product.getName());
    }

    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> issues;
        private final List<String> checkedFields;
    }
}
