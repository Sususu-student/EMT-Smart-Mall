package com.smartmall.agent.service;

import com.smartmall.agent.dto.AgentStep;
import com.smartmall.agent.dto.CompareRequest;
import com.smartmall.agent.dto.CompareResponse;
import com.smartmall.agent.entity.CompareRecord;
import com.smartmall.agent.llm.LlmService;
import com.smartmall.agent.mapper.CompareRecordMapper;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import com.smartmall.product.dto.AttributeMeta;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompareAgentService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final CompareRecordMapper compareRecordMapper;
    private final LlmService llmService;
    private final AgentLogService logService;
    private final PromptService promptService;

    private static final Pattern MONEY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");

    public CompareResponse compare(CompareRequest request, String userId, String taskId, String conversationId) {
        String traceId = taskId == null ? Ids.next() : taskId;
        List<String> ids = request.getProductIds() == null ? List.of()
                : request.getProductIds().stream().filter(StringUtils::hasText).distinct().toList();
        if (ids.size() < 2) {
            throw new BizException("至少选择 2 件商品进行对比");
        }
        if (ids.size() > 4) {
            throw new BizException("一次最多对比 4 件商品");
        }

        var matrixTrace = logService.start(traceId, conversationId, "compare-agent", "build_matrix",
                "构建对比矩阵", "商品: " + String.join(",", ids));
        List<Product> products = productMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                .in(Product::getId, ids));
        Map<String, Product> byId = new LinkedHashMap<>();
        products.forEach(p -> byId.put(p.getId(), p));
        List<Product> ordered = ids.stream().map(byId::get).filter(p -> p != null).toList();
        if (ordered.size() < 2) {
            matrixTrace.failed("有效商品不足 2 件");
            throw BizException.notFound("部分商品不存在或已下架");
        }

        long categoryCount = ordered.stream().map(Product::getCategoryId).distinct().count();
        if (categoryCount > 1) {
            matrixTrace.failed("跨品类对比被拦截");
            // 说明「为什么不行」，而不是只下命令，用户才知道下一步怎么做
            throw new BizException("这几件商品的品类不同，参数维度对不上，没法放在一起比。换成同品类的商品再试试");
        }

        CompareResponse response = new CompareResponse();
        response.setTaskId(traceId);
        for (Product product : ordered) {
            CompareResponse.CompareProduct cp = new CompareResponse.CompareProduct();
            cp.setId(product.getId());
            cp.setName(product.getName());
            cp.setImage(product.getImage());
            cp.setImageFallback(product.getImageFallback());
            cp.setPrice(product.getPrice());
            cp.setRating(product.getRating());
            cp.setStock(product.getStock());
            cp.setBrand(product.getBrand());
            response.getProducts().add(cp);
        }

        Category category = categoryMapper.selectById(ordered.get(0).getCategoryId());
        List<AttributeMeta> schema = parseSchema(category);
        Map<String, Double> weights = request.getWeights() == null ? Map.of() : request.getWeights();

        List<DimensionData> dimensions = new ArrayList<>();
        dimensions.add(priceDimension(ordered, weightFor(weights, "价格", "price", 1.0)));
        dimensions.add(ratingDimension(ordered, weightFor(weights, "评分", "rating", 0.6)));
        for (AttributeMeta meta : schema) {
            dimensions.add(attributeDimension(ordered, meta, weightFor(weights, meta.getKey(),
                    meta.getKey(), meta.getWeight() == null ? 1.0 : meta.getWeight().doubleValue())));
        }
        for (DimensionData dimension : dimensions) {
            response.getDimensions().add(dimension.toDto());
        }
        matrixTrace.success("生成 " + dimensions.size() + " 个对比维度");

        var scoreTrace = logService.start(traceId, conversationId, "compare-agent", "weighted_score",
                "多维度加权评分", "维度数: " + dimensions.size());
        double weightSum = dimensions.stream().mapToDouble(d -> d.weight).sum();
        List<CompareResponse.ProductScore> scores = new ArrayList<>();
        for (Product product : ordered) {
            CompareResponse.ProductScore score = new CompareResponse.ProductScore();
            score.setProductId(product.getId());
            double total = 0;
            for (DimensionData dimension : dimensions) {
                double value = dimension.normalized.getOrDefault(product.getId(), 0.0);
                double contribution = weightSum == 0 ? 0 : value * dimension.weight / weightSum * 100;
                score.getContributions().put(dimension.key, round(contribution, 1));
                total += contribution;
            }
            score.setTotalScore(round(total, 1));
            scores.add(score);
        }
        scores.sort(Comparator.comparingDouble(CompareResponse.ProductScore::getTotalScore).reversed());
        for (int i = 0; i < scores.size(); i++) {
            scores.get(i).setRank(i + 1);
        }
        response.setScores(scores);
        scoreTrace.success("最高分 " + scores.get(0).getTotalScore() + "，商品 " + scores.get(0).getProductId());

        CompareResponse.ProductScore winner = scores.get(0);
        Product winnerProduct = byId.get(winner.getProductId());
        response.setRecommendedId(winner.getProductId());
        response.setRecommendedName(winnerProduct == null ? "" : winnerProduct.getName());

        var adviceTrace = logService.start(traceId, conversationId, "compare-agent", "advice",
                "生成决策建议", "推荐商品: " + response.getRecommendedName());
        String advice = generateAdvice(response, ordered, dimensions, winner);
        boolean llmUsed = advice != null;
        if (advice == null) {
            advice = templateAdvice(response, ordered, dimensions, winner);
        }
        response.setAdvice(advice);
        response.setDegraded(!llmUsed);
        response.setHighlights(buildHighlights(response, dimensions, winner));
        adviceTrace.success("建议长度 " + advice.length() + "，来源 " + (llmUsed ? "LLM" : "规则"));

        if (userId != null) {
            CompareRecord record = new CompareRecord();
            record.setUserId(userId);
            record.setProductIds(JsonUtils.toJson(ids));
            record.setResult(JsonUtils.toJson(response));
            record.setCreatedAt(LocalDateTime.now());
            compareRecordMapper.insert(record);
        }
        return response;
    }

    private DimensionData priceDimension(List<Product> products, double weight) {
        DimensionData dimension = new DimensionData("价格", "元", "number", false, weight);
        for (Product product : products) {
            dimension.raw.put(product.getId(), product.getPrice());
            dimension.display.put(product.getId(), product.getPrice().stripTrailingZeros().toPlainString());
        }
        dimension.normalizeNumeric();
        return dimension;
    }

    private DimensionData ratingDimension(List<Product> products, double weight) {
        DimensionData dimension = new DimensionData("评分", "分", "number", true, weight);
        for (Product product : products) {
            dimension.raw.put(product.getId(), product.getRating());
            dimension.display.put(product.getId(),
                    product.getRating() == null ? "—" : product.getRating().stripTrailingZeros().toPlainString());
        }
        dimension.normalizeNumeric();
        return dimension;
    }

    private DimensionData attributeDimension(List<Product> products, AttributeMeta meta, double weight) {
        DimensionData dimension = new DimensionData(meta.getKey(), meta.getUnit(), meta.getType(),
                !Boolean.FALSE.equals(meta.getHigherBetter()), weight);
        for (Product product : products) {
            Map<String, Object> attrs = JsonUtils.toMap(product.getAttributes());
            Object value = attrs.get(meta.getKey());
            if (value == null) {
                dimension.display.put(product.getId(), "—");
                dimension.raw.put(product.getId(), null);
                continue;
            }
            dimension.display.put(product.getId(), String.valueOf(value));
            if ("enum".equals(meta.getType()) && meta.getOptions() != null) {
                int index = meta.getOptions().indexOf(String.valueOf(value));
                dimension.raw.put(product.getId(), index < 0 ? null : (double) index);
                dimension.optionSize = meta.getOptions().size();
            } else {
                dimension.raw.put(product.getId(), parseNumber(value));
            }
        }
        if ("enum".equals(meta.getType())) {
            dimension.normalizeEnum();
        } else {
            dimension.normalizeNumeric();
        }
        return dimension;
    }

    private String generateAdvice(CompareResponse response, List<Product> products,
                                  List<DimensionData> dimensions, CompareResponse.ProductScore winner) {
        if (!llmService.isAvailable()) {
            return null;
        }
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Product product : products) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("id", product.getId());
            fact.put("name", product.getName());
            fact.put("price", product.getPrice());
            fact.put("attributes", JsonUtils.toMap(product.getAttributes()));
            fact.put("score", response.getScores().stream()
                    .filter(s -> s.getProductId().equals(product.getId())).findFirst()
                    .map(CompareResponse.ProductScore::getTotalScore).orElse(0.0));
            facts.add(fact);
        }
        String system = promptService.text("compare.system");
        String user = "对比矩阵: " + JsonUtils.toJson(response.getDimensions())
                + "\n商品事实: " + JsonUtils.toJson(facts)
                + "\n加权得分: " + JsonUtils.toJson(response.getScores())
                + "\n推荐商品 ID: " + winner.getProductId();
        String advice = llmService.complete(system, user);
        if (advice == null || advice.length() < 20 || advice.length() > 300) {
            return null;
        }
        if (!pricesSupported(advice, products)) {
            log.info("对比建议中的价格与商品库不一致，降级为模板建议");
            return null;
        }
        return advice.replaceAll("[*#]", "").trim();
    }

    private boolean pricesSupported(String advice, List<Product> products) {
        Set<BigDecimal> prices = new LinkedHashSet<>();
        for (Product product : products) {
            prices.add(product.getPrice().stripTrailingZeros());
        }
        for (int i = 0; i < products.size(); i++) {
            for (int j = i + 1; j < products.size(); j++) {
                prices.add(products.get(i).getPrice().subtract(products.get(j).getPrice()).abs().stripTrailingZeros());
            }
        }
        Matcher matcher = MONEY.matcher(advice);
        while (matcher.find()) {
            BigDecimal claimed = new BigDecimal(matcher.group(1)).stripTrailingZeros();
            if (prices.stream().noneMatch(p -> p.compareTo(claimed) == 0)) {
                return false;
            }
        }
        return true;
    }

    private String templateAdvice(CompareResponse response, List<Product> products,
                                  List<DimensionData> dimensions, CompareResponse.ProductScore winner) {
        Product winnerProduct = products.stream().filter(p -> p.getId().equals(winner.getProductId()))
                .findFirst().orElse(products.get(0));
        List<String> winnerBest = dimensions.stream()
                .filter(d -> strictlyBest(d, winnerProduct.getId(), products))
                .map(d -> d.key).limit(2).toList();
        CompareResponse.ProductScore runnerUp = response.getScores().size() > 1 ? response.getScores().get(1) : null;
        StringBuilder advice = new StringBuilder();
        advice.append("综合 ").append(dimensions.size()).append(" 个维度加权评分，最推荐「")
                .append(winnerProduct.getName()).append("」（").append(winner.getTotalScore()).append(" 分）。");
        if (!winnerBest.isEmpty()) {
            advice.append("它在").append(String.join("、", winnerBest)).append("上更占优");
        } else {
            advice.append("它的整体参数更均衡");
        }
        if (winnerBest.isEmpty()) {
            advice.append("（各项维度比较接近）");
        }
        if (runnerUp != null) {
            Product runnerProduct = products.stream().filter(p -> p.getId().equals(runnerUp.getProductId()))
                    .findFirst().orElse(null);
            List<String> runnerBest = dimensions.stream()
                    .filter(d -> strictlyBetter(d, runnerUp.getProductId(), winnerProduct.getId()))
                    .map(d -> d.key).distinct().limit(2).toList();
            if (runnerProduct != null) {
                advice.append("；「").append(runnerProduct.getName()).append("」（")
                        .append(runnerUp.getTotalScore()).append(" 分）");
                if (!runnerBest.isEmpty()) {
                    advice.append("仅在").append(String.join("、", runnerBest)).append("上略强");
                } else {
                    advice.append("但各维度均未超过推荐款");
                }
                advice.append("，综合得分低 ").append(round(winner.getTotalScore() - runnerUp.getTotalScore(), 1)).append(" 分");
            }
        }
        advice.append("。如果更看重某一维度，可以在对比面板调整权重后再决策。");
        return advice.toString();
    }

    private List<String> buildHighlights(CompareResponse response, List<DimensionData> dimensions,
                                         CompareResponse.ProductScore winner) {
        List<String> highlights = new ArrayList<>();
        for (DimensionData dimension : dimensions) {
            if (winner.getProductId().equals(dimension.bestId)) {
                String value = dimension.display.getOrDefault(winner.getProductId(), "");
                highlights.add(dimension.key + " " + value + (dimension.unit == null ? "" : dimension.unit));
            }
            if (highlights.size() >= 4) {
                break;
            }
        }
        if (highlights.isEmpty()) {
            highlights.add("综合评分 " + winner.getTotalScore());
        }
        return highlights;
    }

    private boolean strictlyBetter(DimensionData dimension, String candidate, String baseline) {
        return dimension.normalized.getOrDefault(candidate, 0.0)
                > dimension.normalized.getOrDefault(baseline, 0.0) + 1e-9;
    }

    private boolean strictlyBest(DimensionData dimension, String candidate, List<Product> products) {
        double value = dimension.normalized.getOrDefault(candidate, 0.0);
        for (Product product : products) {
            if (product.getId().equals(candidate)) {
                continue;
            }
            if (dimension.normalized.getOrDefault(product.getId(), 0.0) >= value - 1e-9) {
                return false;
            }
        }
        return true;
    }

    private double weightFor(Map<String, Double> weights, String label, String alias, double fallback) {
        Double value = weights.get(label);
        if (value == null) {
            value = weights.get(alias);
        }
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(5, Math.max(0.1, value));
    }

    private List<AttributeMeta> parseSchema(Category category) {
        List<AttributeMeta> metas = new ArrayList<>();
        if (category == null) {
            return metas;
        }
        for (Map<String, Object> map : JsonUtils.toMapList(category.getAttributeSchema())) {
            AttributeMeta meta = new AttributeMeta();
            meta.setKey(String.valueOf(map.get("key")));
            meta.setUnit(map.get("unit") == null ? null : String.valueOf(map.get("unit")));
            meta.setType(map.get("type") == null ? "number" : String.valueOf(map.get("type")));
            meta.setHigherBetter(!Boolean.FALSE.equals(map.get("higherBetter")));
            if (map.get("weight") != null) {
                meta.setWeight(new BigDecimal(String.valueOf(map.get("weight"))));
            }
            if (map.get("options") instanceof List<?> options) {
                meta.setOptions(options.stream().map(String::valueOf).toList());
            }
            metas.add(meta);
        }
        return metas;
    }

    private Double parseNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).replaceAll("[^0-9.\\-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static class DimensionData {
        private final String key;
        private final String unit;
        private final String type;
        private final boolean higherBetter;
        private final double weight;
        private final Map<String, Object> raw = new LinkedHashMap<>();
        private final Map<String, String> display = new LinkedHashMap<>();
        private final Map<String, Double> normalized = new LinkedHashMap<>();
        private int optionSize;
        private String bestId;

        private DimensionData(String key, String unit, String type, boolean higherBetter, double weight) {
            this.key = key;
            this.unit = unit;
            this.type = type;
            this.higherBetter = higherBetter;
            this.weight = weight;
        }

        private void normalizeNumeric() {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (Object value : raw.values()) {
                if (value instanceof Number number) {
                    min = Math.min(min, number.doubleValue());
                    max = Math.max(max, number.doubleValue());
                }
            }
            boolean constant = min == max;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (!(entry.getValue() instanceof Number number)) {
                    normalized.put(entry.getKey(), 0.0);
                    continue;
                }
                double value = number.doubleValue();
                double score;
                if (constant) {
                    score = 0.8;
                } else if (higherBetter) {
                    score = (value - min) / (max - min);
                } else {
                    score = (max - value) / (max - min);
                }
                normalized.put(entry.getKey(), score);
            }
            pickBest();
        }

        private void normalizeEnum() {
            int maxIndex = Math.max(1, optionSize - 1);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (!(entry.getValue() instanceof Number number)) {
                    normalized.put(entry.getKey(), 0.0);
                    continue;
                }
                double score = number.doubleValue() / maxIndex;
                if (!higherBetter) {
                    score = 1 - score;
                }
                normalized.put(entry.getKey(), score);
            }
            pickBest();
        }

        private void pickBest() {
            bestId = null;
            double best = -1;
            for (Map.Entry<String, Double> entry : normalized.entrySet()) {
                if (entry.getValue() > best) {
                    best = entry.getValue();
                    bestId = entry.getKey();
                }
            }
        }

        private CompareResponse.CompareDimension toDto() {
            CompareResponse.CompareDimension dto = new CompareResponse.CompareDimension();
            dto.setKey(key);
            dto.setUnit(unit);
            dto.setType(type);
            dto.setHigherBetter(higherBetter);
            dto.setWeight(weight);
            dto.setDisplayValues(display);
            dto.setNormalizedValues(normalized);
            dto.setBestProductId(bestId);
            return dto;
        }
    }
}
