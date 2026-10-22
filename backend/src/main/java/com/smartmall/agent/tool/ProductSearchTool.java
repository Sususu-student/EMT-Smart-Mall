package com.smartmall.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.agent.dto.ScoredProduct;
import com.smartmall.agent.dto.Slots;
import com.smartmall.common.JsonUtils;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 商品检索工具（对应设计文档中的 searchProducts 工具）。
 * 硬过滤：品类、价格、上架状态；软排序：关键词、功能偏好、场景、评分与销量。
 */
@Service
@RequiredArgsConstructor
public class ProductSearchTool {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductService productService;

    public SearchOutcome search(Slots slots, int limit) {
        if (slots == null || (!slots.hasCategory() && !StringUtils.hasText(slots.getKeyword()))) {
            return new SearchOutcome(List.of(), 0, false, "缺少品类或关键词");
        }
        // 不自动放宽价格：预算 10 元却推荐 399 元商品会破坏可解释性。
        // 没有匹配结果时，由导购 Agent 明确提示用户提高预算或更换条件。
        return doSearch(slots, limit, true);
    }

    private SearchOutcome doSearch(Slots slots, int limit, boolean applyPrice) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1);
        if (slots.hasCategory()) {
            List<String> categoryIds = productService.descendantIds(slots.getCategoryId());
            if (categoryIds.isEmpty()) {
                return new SearchOutcome(List.of(), 0, false, "品类不存在");
            }
            wrapper.in(Product::getCategoryId, categoryIds);
        }
        if (applyPrice) {
            if (slots.getMaxPrice() != null) {
                wrapper.le(Product::getPrice, slots.getMaxPrice());
            }
            if (slots.getMinPrice() != null) {
                wrapper.ge(Product::getPrice, slots.getMinPrice());
            }
        }
        List<Product> products = productMapper.selectList(wrapper.last("LIMIT 200"));
        List<ScoredProduct> scored = new ArrayList<>();
        String keyword = slots.getKeyword();
        for (Product product : products) {
            double score = 0;
            List<String> highlights = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String name = product.getName() == null ? "" : product.getName();
                String subtitle = product.getSubtitle() == null ? "" : product.getSubtitle();
                String tags = product.getTags() == null ? "" : product.getTags();
                if (name.contains(keyword)) {
                    score += 10;
                    highlights.add("名称匹配");
                } else if (subtitle.contains(keyword) || tags.contains(keyword)) {
                    score += 5;
                } else if (product.getBrand() != null && product.getBrand().contains(keyword)) {
                    score += 3;
                }
            }
            for (String feature : slots.getFeatures()) {
                String attrText = (product.getAttributes() == null ? "" : product.getAttributes())
                        + (product.getTags() == null ? "" : product.getTags())
                        + (product.getName() == null ? "" : product.getName());
                if (matchesFeature(feature, attrText)) {
                    score += 4;
                    highlights.add(feature);
                }
            }
            if (StringUtils.hasText(slots.getScene())) {
                String text = (product.getTags() == null ? "" : product.getTags())
                        + (product.getSubtitle() == null ? "" : product.getSubtitle())
                        + (product.getName() == null ? "" : product.getName());
                if (text.contains(slots.getScene()) || sceneRelated(slots.getScene(), text)) {
                    score += 3;
                }
            }
            if (StringUtils.hasText(slots.getBrand()) && slots.getBrand().equals(product.getBrand())) {
                score += 5;
                highlights.add("品牌匹配");
            }
            if (product.getRating() != null) {
                score += (product.getRating().doubleValue() - 4.0) * 3;
            }
            if (product.getSales() != null) {
                score += Math.log10(product.getSales() + 1);
            }
            if (slots.getMaxPrice() != null && product.getPrice().compareTo(slots.getMaxPrice()) <= 0) {
                score += 1.5;
            }
            scored.add(new ScoredProduct(product, score, highlights));
        }
        scored.sort(Comparator.comparingDouble(ScoredProduct::getScore).reversed()
                .thenComparing(sp -> sp.getProduct().getPrice()));
        List<ScoredProduct> top = scored.stream().limit(Math.max(1, Math.min(limit, 8))).toList();
        return new SearchOutcome(top, scored.size(), false, "匹配 " + scored.size() + " 件，返回前 " + top.size() + " 件");
    }

    private boolean matchesFeature(String feature, String attrText) {
        if (attrText == null) {
            return false;
        }
        String text = attrText + " " + feature;
        return switch (feature) {
            case "防水" -> attrText.contains("防水") || attrText.contains("IPX") || attrText.contains("ATM");
            case "降噪" -> attrText.contains("降噪") && !attrText.contains("通话降噪");
            case "续航" -> attrText.contains("续航") || attrText.contains("电池");
            case "轻量" -> attrText.contains("重量");
            case "大容量" -> attrText.contains("容量") || attrText.contains("水箱");
            case "静音" -> attrText.contains("噪音") || attrText.contains("静音");
            case "自动集尘" -> attrText.contains("集尘") || attrText.contains("扫拖");
            default -> attrText.contains(feature) || text.contains(feature);
        };
    }

    private boolean sceneRelated(String scene, String text) {
        return switch (scene) {
            case "跑步" -> text.contains("跑步") || text.contains("运动") || text.contains("跑鞋");
            case "通勤" -> text.contains("通勤") || text.contains("降噪") || text.contains("便携");
            case "办公" -> text.contains("办公") || text.contains("键盘") || text.contains("轻薄");
            case "家用" -> text.contains("家用") || text.contains("家庭");
            case "旅行" -> text.contains("旅行") || text.contains("便携") || text.contains("背包");
            default -> false;
        };
    }

    public Map<String, Category> categoryMap() {
        return productService.categoryMap();
    }

    public double scoreOf(Product product, Slots slots) {
        if (product == null) {
            return 0;
        }
        double score = 0;
        if (slots != null && slots.getFeatures() != null) {
            String attrText = (product.getAttributes() == null ? "" : product.getAttributes())
                    + (product.getTags() == null ? "" : product.getTags());
            for (String feature : slots.getFeatures()) {
                if (matchesFeature(feature, attrText)) {
                    score += 4;
                }
            }
        }
        if (product.getRating() != null) {
            score += (product.getRating().doubleValue() - 4.0) * 3;
        }
        return score;
    }

    public record SearchOutcome(List<ScoredProduct> products, int total, boolean relaxed, String note) {
        public List<Product> rawProducts() {
            return products.stream().map(ScoredProduct::getProduct).toList();
        }
    }
}
