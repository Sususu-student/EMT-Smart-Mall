package com.smartmall.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.agent.dto.Slots;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.common.JsonUtils;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 槽位提取与合并：规则先行，LLM 补充；两者都只产出结构化字段。
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final LlmService llmService;

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("蓝牙耳机", List.of("蓝牙耳机", "耳机", "耳麦", "降噪耳机", "运动耳机", "tws")),
            Map.entry("智能手机", List.of("智能手机", "手机", "5g手机", "拍照手机")),
            Map.entry("智能手表", List.of("智能手表", "手表", "运动手表")),
            Map.entry("笔记本电脑", List.of("笔记本电脑", "笔记本", "轻薄本", "游戏本", "电脑")),
            Map.entry("机械键盘", List.of("机械键盘", "键盘")),
            Map.entry("跑步鞋", List.of("跑步鞋", "跑鞋", "运动鞋", "慢跑鞋")),
            Map.entry("瑜伽垫", List.of("瑜伽垫", "健身垫")),
            Map.entry("护肤品", List.of("护肤品", "护肤", "面霜", "精华", "乳液", "防晒霜")),
            Map.entry("咖啡机", List.of("咖啡机", "意式咖啡机", "半自动咖啡机")),
            Map.entry("空气净化器", List.of("空气净化器", "净化器", "除醛")),
            Map.entry("咖啡豆", List.of("咖啡豆", "咖啡粉")),
            Map.entry("双肩包", List.of("双肩包", "背包", "电脑包")),
            Map.entry("扫地机器人", List.of("扫地机器人", "扫地机", "扫拖机器人"))
    );

    private static final List<String> FEATURES = List.of(
            "防水", "降噪", "续航", "轻量", "透气", "缓震", "静音", "大容量", "快充", "便携",
            "防滑", "防晒", "保湿", "护眼", "无线", "三模", "自动集尘", "除甲醛");

    private static final Map<String, String> SCENES = Map.ofEntries(
            Map.entry("跑步", "跑步"), Map.entry("运动", "运动"), Map.entry("健身", "健身"),
            Map.entry("通勤", "通勤"), Map.entry("上班", "通勤"), Map.entry("办公", "办公"),
            Map.entry("学习", "学习"), Map.entry("旅行", "旅行"), Map.entry("出差", "旅行"),
            Map.entry("家用", "家用"), Map.entry("居家", "家用"), Map.entry("送礼", "送礼"),
            Map.entry("游戏", "游戏"), Map.entry("户外", "户外"), Map.entry("越野", "户外"));

    private static final Pattern RANGE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:-|~|到|至)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:元|块)?");
    private static final Pattern MAX_WORD = Pattern.compile(
            "(?:预算|价格|不超过|最多|控制在|低于|小于|别超过|只要)\\s*(?:是|为|在|大概|约)?\\s*(\\d+(?:\\.\\d+)?)\\s*(千元|万|元|块)?");
    private static final Pattern MAX_SUFFIX = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:元|块)?\\s*(?:以内|以下|之内)");
    private static final Pattern MIN_SUFFIX = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:元|块)?\\s*(?:以上|起)");
    private static final Pattern YUAN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块)");
    private static final Pattern QIAN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*千元");
    private static final Pattern PRODUCT_ID = Pattern.compile("p_[a-z_]+_\\d{2}");

    public Slots extract(String message, Slots current, List<String> context) {
        String text = message == null ? "" : message.trim();
        Slots extracted = new Slots();
        fillByRules(text, extracted);

        if (llmService.isAvailable()) {
            Slots llmSlots = extractByLlm(text, current, context);
            if (llmSlots != null) {
                mergeMissing(extracted, llmSlots);
            }
        }
        return merge(current == null ? new Slots() : current, extracted);
    }

    public Slots merge(Slots current, Slots extracted) {
        Slots merged = new Slots();
        merged.setAskedBudget(current.isAskedBudget());
        merged.setBudgetUnlimited(current.isBudgetUnlimited());
        merged.setCategoryId(current.getCategoryId());
        merged.setCategoryName(current.getCategoryName());
        merged.setKeyword(current.getKeyword());
        merged.setBrand(current.getBrand());
        merged.setScene(current.getScene());
        merged.setMinPrice(current.getMinPrice());
        merged.setMaxPrice(current.getMaxPrice());
        merged.setFeatures(new ArrayList<>(current.getFeatures()));
        merged.setLastProductIds(new ArrayList<>(current.getLastProductIds()));
        merged.setLastProductNames(new ArrayList<>(current.getLastProductNames()));
        merged.setPending(current.getPending());

        if (extracted.getCategoryId() != null) {
            if (!extracted.getCategoryId().equals(merged.getCategoryId())) {
                merged.setKeyword(null);
                merged.setBrand(null);
            }
            merged.setCategoryId(extracted.getCategoryId());
            merged.setCategoryName(extracted.getCategoryName());
        }
        if (extracted.getKeyword() != null && !extracted.getKeyword().isBlank()) {
            merged.setKeyword(extracted.getKeyword());
        }
        if (extracted.getBrand() != null) {
            merged.setBrand(extracted.getBrand());
        }
        if (extracted.getScene() != null) {
            merged.setScene(extracted.getScene());
        }
        if (extracted.getMinPrice() != null) {
            merged.setMinPrice(extracted.getMinPrice());
            merged.setBudgetUnlimited(false);
        }
        if (extracted.getMaxPrice() != null) {
            merged.setMaxPrice(extracted.getMaxPrice());
            merged.setBudgetUnlimited(false);
        }
        if (extracted.isBudgetUnlimited()) {
            merged.setBudgetUnlimited(true);
            merged.setMaxPrice(null);
            merged.setMinPrice(null);
        }
        if (extracted.getFeatures() != null && !extracted.getFeatures().isEmpty()) {
            LinkedHashSet<String> features = new LinkedHashSet<>(merged.getFeatures());
            features.addAll(extracted.getFeatures());
            merged.setFeatures(new ArrayList<>(features));
        }
        merged.setPending(extracted.getPending() != null ? extracted.getPending() : merged.getPending());
        return merged;
    }

    private void fillByRules(String text, Slots slots) {
        String lower = text.toLowerCase(Locale.ROOT);
        CategoryMatch category = matchCategory(text);
        if (category != null) {
            slots.setCategoryId(category.id());
            slots.setCategoryName(category.name());
        }

        if (text.contains("预算不限") || text.contains("价格不限") || text.contains("都行")
                || text.contains("随便") || text.contains("无所谓")) {
            slots.setBudgetUnlimited(true);
        }
        extractPrice(text, slots);

        for (String brand : brands()) {
            if (brand.length() >= 2 && text.contains(brand)) {
                slots.setBrand(brand);
                break;
            }
        }
        for (Map.Entry<String, String> scene : SCENES.entrySet()) {
            if (text.contains(scene.getKey())) {
                slots.setScene(scene.getValue());
                break;
            }
        }
        List<String> features = new ArrayList<>();
        for (String feature : FEATURES) {
            if (text.contains(feature)) {
                features.add(feature);
            }
        }
        if (text.contains("长续航") && !features.contains("续航")) {
            features.add("续航");
        }
        slots.setFeatures(features);

        if (category == null && slots.getKeyword() == null) {
            String keyword = cleanKeyword(text);
            if (keyword != null) {
                slots.setKeyword(keyword);
            }
        }
        if (PRODUCT_ID.matcher(text).find()) {
            Matcher matcher = PRODUCT_ID.matcher(text);
            if (matcher.find()) {
                slots.setPending("product:" + matcher.group());
            }
        }
    }

    private void extractPrice(String text, Slots slots) {
        Matcher range = RANGE.matcher(text);
        if (range.find()) {
            BigDecimal low = new BigDecimal(range.group(1));
            BigDecimal high = new BigDecimal(range.group(2));
            slots.setMinPrice(low.min(high));
            slots.setMaxPrice(low.max(high));
            return;
        }
        Matcher maxWord = MAX_WORD.matcher(text);
        if (maxWord.find()) {
            slots.setMaxPrice(scale(new BigDecimal(maxWord.group(1)), maxWord.group(2)));
            return;
        }
        Matcher maxSuffix = MAX_SUFFIX.matcher(text);
        if (maxSuffix.find()) {
            slots.setMaxPrice(new BigDecimal(maxSuffix.group(1)));
            return;
        }
        Matcher minSuffix = MIN_SUFFIX.matcher(text);
        if (minSuffix.find()) {
            slots.setMinPrice(new BigDecimal(minSuffix.group(1)));
            return;
        }
        Matcher qian = QIAN.matcher(text);
        if (qian.find()) {
            slots.setMaxPrice(new BigDecimal(qian.group(1)).multiply(BigDecimal.valueOf(1000)));
            return;
        }
        Matcher yuan = YUAN.matcher(text);
        if (yuan.find()) {
            slots.setMaxPrice(new BigDecimal(yuan.group(1)));
        }
    }

    private BigDecimal scale(BigDecimal value, String unit) {
        if ("千元".equals(unit) || "千".equals(unit)) {
            return value.multiply(BigDecimal.valueOf(1000));
        }
        if ("万".equals(unit)) {
            return value.multiply(BigDecimal.valueOf(10000));
        }
        return value;
    }

    private CategoryMatch matchCategory(String text) {
        List<Category> leaves = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .isNotNull(Category::getParentId))
                .stream().sorted(Comparator.comparingInt((Category c) -> c.getName().length()).reversed())
                .toList();
        for (Category leaf : leaves) {
            if (text.contains(leaf.getName())) {
                return new CategoryMatch(leaf.getId(), leaf.getName());
            }
        }
        for (Category leaf : leaves) {
            for (String alias : ALIASES.getOrDefault(leaf.getName(), List.of())) {
                if (text.contains(alias)) {
                    return new CategoryMatch(leaf.getId(), leaf.getName());
                }
            }
        }
        return null;
    }

    private String cleanKeyword(String text) {
        String cleaned = text
                .replaceAll("我想买|我想买个|想买|想买个|帮我|请|推荐|有没有|想要|一个|一款|一些|可以|适合|的", " ")
                .replaceAll("预算|价格|不超过|最多|控制在|以内|以下|左右|块钱|块钱内", " ")
                .replaceAll("\\d+(?:\\.\\d+)?\\s*(?:元|块|千元|万)?", " ")
                .replaceAll("[，。！？,.!?、]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() < 2) {
            return null;
        }
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }

    private Slots extractByLlm(String text, Slots current, List<String> context) {
        List<String> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1).isNotNull(Category::getParentId))
                .stream().map(Category::getName).toList();
        String system = """
                你是电商导购的需求解析器。从用户消息中抽取购物条件，只输出 JSON，不要解释。
                字段: category(品类，必须是给定品类之一), keyword(搜索词), minPrice(数字), maxPrice(数字),
                brand(品牌), scene(使用场景), features(功能偏好数组)。
                用户没提到的字段输出 null，不要臆造。价格单位是元。
                可选品类: %s
                """.formatted(String.join("、", categories));
        String user = "已有槽位: " + JsonUtils.toJson(current)
                + "\n近期对话:\n" + String.join("\n", context)
                + "\n当前消息: " + text
                + "\n输出示例: {\"category\":\"蓝牙耳机\",\"keyword\":null,\"minPrice\":null,\"maxPrice\":300,"
                + "\"brand\":null,\"scene\":\"跑步\",\"features\":[\"防水\"]}";
        String answer = llmService.complete(system, user);
        if (answer == null) {
            return null;
        }
        Map<String, Object> json = JsonUtils.toMap(JsonUtils.extractJson(answer));
        if (json.isEmpty()) {
            return null;
        }
        Slots slots = new Slots();
        Object category = json.get("category");
        if (category != null && !"null".equals(String.valueOf(category))) {
            Category leaf = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, String.valueOf(category))
                    .eq(Category::getStatus, 1).last("LIMIT 1"));
            if (leaf != null) {
                slots.setCategoryId(leaf.getId());
                slots.setCategoryName(leaf.getName());
            }
        }
        slots.setKeyword(textValue(json.get("keyword")));
        slots.setBrand(textValue(json.get("brand")));
        slots.setScene(textValue(json.get("scene")));
        slots.setMinPrice(numberValue(json.get("minPrice")));
        slots.setMaxPrice(numberValue(json.get("maxPrice")));
        Object features = json.get("features");
        if (features instanceof List<?> list) {
            slots.setFeatures(list.stream().map(String::valueOf).filter(s -> !s.isBlank() && !"null".equals(s)).toList());
        }
        return slots;
    }

    private void mergeMissing(Slots target, Slots source) {
        if (target.getCategoryId() == null) {
            target.setCategoryId(source.getCategoryId());
            target.setCategoryName(source.getCategoryName());
        }
        if (target.getKeyword() == null) {
            target.setKeyword(source.getKeyword());
        }
        if (target.getBrand() == null) {
            target.setBrand(source.getBrand());
        }
        if (target.getScene() == null) {
            target.setScene(source.getScene());
        }
        if (target.getMinPrice() == null) {
            target.setMinPrice(source.getMinPrice());
        }
        if (target.getMaxPrice() == null) {
            target.setMaxPrice(source.getMaxPrice());
        }
        if (!target.isBudgetUnlimited()) {
            target.setBudgetUnlimited(source.isBudgetUnlimited());
        }
        if (target.getFeatures().isEmpty()) {
            target.setFeatures(source.getFeatures());
        }
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private BigDecimal numberValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(String.valueOf(value).replace(",", ""));
            return number.compareTo(BigDecimal.ZERO) > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<String> brands() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>().select(Product::getBrand))
                .stream().map(Product::getBrand).filter(b -> b != null && !b.isBlank()).distinct().toList();
    }

    /**
     * 解析用户消息中的商品指代，如“A款”“第二个”“对比 p_headphone_01”。
     */
    public List<String> referencedIds(String message, Slots slots) {
        List<String> ids = new ArrayList<>();
        List<String> candidates = slots.getLastProductIds();
        if (candidates == null || candidates.isEmpty()) {
            return ids;
        }
        Matcher idMatcher = PRODUCT_ID.matcher(message);
        while (idMatcher.find()) {
            if (candidates.contains(idMatcher.group())) {
                ids.add(idMatcher.group());
            }
        }
        if (!ids.isEmpty()) {
            return ids;
        }
        String text = message == null ? "" : message;
        String[] marks = {"a", "b", "c", "d"};
        String[] cn = {"一", "二", "三", "四"};
        for (int i = 0; i < candidates.size() && i < 4; i++) {
            boolean hit = text.toLowerCase(Locale.ROOT).matches(".*\\b" + marks[i] + "\\b.*")
                    || text.contains(marks[i].toUpperCase(Locale.ROOT) + "款")
                    || text.contains(marks[i].toLowerCase(Locale.ROOT) + "款")
                    || text.contains("第" + (i + 1) + "个")
                    || text.contains("第" + cn[i] + "个")
                    || text.contains((i + 1) + "号");
            if (hit && !ids.contains(candidates.get(i))) {
                ids.add(candidates.get(i));
            }
        }
        for (int i = 0; i < slots.getLastProductNames().size() && i < candidates.size(); i++) {
            String name = slots.getLastProductNames().get(i);
            if (name != null && !name.isBlank() && text.contains(name) && !ids.contains(candidates.get(i))) {
                ids.add(candidates.get(i));
            }
        }
        return ids;
    }

    public String referencedId(String message, Slots slots) {
        List<String> ids = referencedIds(message, slots);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return slots.getLastProductIds().isEmpty() ? null : slots.getLastProductIds().get(0);
    }

    private record CategoryMatch(String id, String name) {
    }
}
