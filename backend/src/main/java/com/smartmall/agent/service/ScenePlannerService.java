package com.smartmall.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.product.dto.ProductQuery;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景搭配师：把「我今天要去海边，预算 1000」这种需求转成一整套商品组合。
 *
 * 思路：
 *   1. 场景关键词 → 一组目标品类
 *   2. 每个品类按评分与销量排序挑选候选
 *   3. 按预算做贪心组合，保证总价不超预算
 *   4. 返回结构化的搭配方案，搭配理由由上层交给 LLM 生成
 */
@Service
@RequiredArgsConstructor
public class ScenePlannerService {

    private final ProductMapper productMapper;

    /** 一个场景的定义：品类序列 + 搭配说明 */
    @Data
    public static class Scene {
        private String key;
        private String name;
        private List<String> keywords;
        /** 品类 id，按重要性排序 */
        private List<Slot> slots;
    }

    @Data
    public static class Slot {
        private String categoryId;
        private String role;
        /** 该角色占预算的权重，用于分配预算 */
        private double weight;

        public Slot(String categoryId, String role, double weight) {
            this.categoryId = categoryId;
            this.role = role;
            this.weight = weight;
        }
    }

    /** 搭配结果 */
    @Data
    public static class PlanItem {
        private String productId;
        private String name;
        private String image;
        private String imageFallback;
        private BigDecimal price;
        private String role;
        private String categoryName;
        private BigDecimal rating;
    }

    @Data
    public static class Plan {
        private String sceneKey;
        private String sceneName;
        private BigDecimal budget;
        private BigDecimal total;
        private boolean withinBudget;
        private List<PlanItem> items = new ArrayList<>();
    }

    /** 场景库：5 个常用场景 */
    private static final List<Scene> SCENES = buildScenes();

    private static List<Scene> buildScenes() {
        List<Scene> list = new ArrayList<>();

        Scene beach = new Scene();
        beach.setKey("beach");
        beach.setName("海边度假");
        beach.setKeywords(List.of("海边", "沙滩", "度假", "海岛", "三亚", "海滨", "旅游"));
        beach.setSlots(List.of(
                new Slot("cat_skincare", "防晒护肤", 0.30),
                new Slot("cat_headphone", "随身音频", 0.35),
                new Slot("cat_backpack", "出行背包", 0.35)
        ));
        list.add(beach);

        Scene commute = new Scene();
        commute.setKey("commute");
        commute.setName("通勤办公");
        commute.setKeywords(List.of("通勤", "上班", "办公", "工位", "公司", "地铁"));
        commute.setSlots(List.of(
                new Slot("cat_keyboard", "办公输入", 0.40),
                new Slot("cat_headphone", "降噪耳机", 0.40),
                new Slot("cat_backpack", "通勤背包", 0.20)
        ));
        list.add(commute);

        Scene fitness = new Scene();
        fitness.setKey("fitness");
        fitness.setName("居家健身");
        fitness.setKeywords(List.of("健身", "在家运动", "瑜伽", "锻炼", "居家运动", "塑形"));
        fitness.setSlots(List.of(
                new Slot("cat_yoga", "瑜伽垫", 0.35),
                new Slot("cat_headphone", "运动耳机", 0.40),
                new Slot("cat_watch", "运动手表", 0.25)
        ));
        list.add(fitness);

        Scene date = new Scene();
        date.setKey("date");
        date.setName("约会穿搭");
        date.setKeywords(List.of("约会", "见面", "相亲", "穿搭", "送礼", "生日礼物"));
        date.setSlots(List.of(
                new Slot("cat_skincare", "护肤打理", 0.40),
                new Slot("cat_watch", "腕表点缀", 0.35),
                new Slot("cat_backpack", "随身小包", 0.25)
        ));
        list.add(date);

        Scene camping = new Scene();
        camping.setKey("camping");
        camping.setName("露营出行");
        camping.setKeywords(List.of("露营", "户外", "徒步", "野营", "登山", "郊游"));
        camping.setSlots(List.of(
                new Slot("cat_backpack", "户外背包", 0.40),
                new Slot("cat_running", "户外鞋履", 0.35),
                new Slot("cat_coffee", "野外咖啡", 0.25)
        ));
        list.add(camping);

        return list;
    }

    /** 从用户原话里识别场景，识别不到返回 null */
    public Scene detectScene(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Scene scene : SCENES) {
            for (String kw : scene.getKeywords()) {
                if (text.contains(kw)) {
                    return scene;
                }
            }
        }
        return null;
    }

    public List<Scene> allScenes() {
        return SCENES;
    }

    /**
     * 按场景与预算生成搭配方案。
     * 预算按槽位权重切分，每个槽位在预算内挑评分最高、销量最好的商品；
     * 若某槽位没有预算内的商品则跳过，保证总价不超预算。
     */
    public Plan plan(Scene scene, BigDecimal budget) {
        Plan plan = new Plan();
        plan.setSceneKey(scene.getKey());
        plan.setSceneName(scene.getName());
        plan.setBudget(budget);

        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            // 没给预算时取一个常见档位
            budget = new BigDecimal("1000");
            plan.setBudget(budget);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Slot slot : scene.getSlots()) {
            BigDecimal slotBudget = budget.multiply(BigDecimal.valueOf(slot.getWeight()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            // 剩余预算也要兜底，避免前面用超
            BigDecimal remaining = budget.subtract(total);
            BigDecimal cap = slotBudget.min(remaining);
            if (cap.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Product product = pickProduct(slot.getCategoryId(), cap);
            if (product == null) {
                continue;
            }
            PlanItem item = new PlanItem();
            item.setProductId(product.getId());
            item.setName(product.getName());
            item.setImage(product.getImage());
            item.setImageFallback(product.getImageFallback());
            item.setPrice(product.getPrice());
            item.setRole(slot.getRole());
            item.setCategoryName(slot.getCategoryId());
            item.setRating(product.getRating());
            plan.getItems().add(item);
            total = total.add(product.getPrice() == null ? BigDecimal.ZERO : product.getPrice());
        }

        plan.setTotal(total);
        plan.setWithinBudget(total.compareTo(budget) <= 0);
        return plan;
    }

    /**
     * 在某品类内选出不超过预算、评分最高的商品。
     * 排序优先评分，其次销量，保证组合方案品质稳定。
     */
    private Product pickProduct(String categoryId, BigDecimal cap) {
        List<Product> candidates = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, categoryId)
                .eq(Product::getStatus, 1)
                .le(Product::getPrice, cap)
                .gt(Product::getStock, 0)
                .orderByDesc(Product::getRating)
                .orderByDesc(Product::getSales)
                .last("LIMIT 5"));
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .max(Comparator
                        .comparing((Product p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating())
                        .thenComparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()))
                .orElse(candidates.get(0));
    }

    /** 生成给 LLM 的方案摘要，用于写搭配理由 */
    public String describe(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("场景：").append(plan.getSceneName())
                .append("；预算：¥").append(plan.getBudget())
                .append("；合计：¥").append(plan.getTotal()).append("\n");
        Map<String, String> roleMap = new LinkedHashMap<>();
        for (PlanItem item : plan.getItems()) {
            sb.append("- ").append(item.getRole()).append("：").append(item.getName())
                    .append("，¥").append(item.getPrice()).append("\n");
            roleMap.put(item.getRole(), item.getName());
        }
        return sb.toString();
    }
}