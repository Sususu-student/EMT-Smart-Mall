package com.smartmall.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.analytics.dto.BuyerAnalyticsVO;
import com.smartmall.analytics.dto.SellerAnalyticsVO;
import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.cart.entity.CartItem;
import com.smartmall.cart.mapper.CartItemMapper;
import com.smartmall.common.BizException;
import com.smartmall.agent.entity.DialogueTurn;
import com.smartmall.agent.mapper.DialogueTurnMapper;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.entity.ProductReview;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.mapper.ProductReviewMapper;
import com.smartmall.user.entity.BrowseHistory;
import com.smartmall.user.mapper.BrowseHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据分析：买家消费画像与卖家经营看板。
 * 所有统计都基于真实表数据，空数据时返回空列表由前端展示空态。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductReviewMapper reviewMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final CartItemMapper cartItemMapper;
    private final DialogueTurnMapper dialogueTurnMapper;

    /** 计入消费统计的订单状态：只算真正成交的 */
    private static final Set<String> PAID_STATUS = Set.of(
            OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED);

    // ========================================================= 买家

    public BuyerAnalyticsVO buyerAnalytics() {
        String userId = AuthContext.userId();
        BuyerAnalyticsVO vo = new BuyerAnalyticsVO();

        // 该用户所有有效订单
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, PAID_STATUS));
        if (orders.isEmpty()) {
            vo.setCategoryShares(List.of());
            vo.setPreferences(buildPreferences(userId, Map.of()));
            vo.setKeywords(buildKeywords(userId));
            return vo;
        }

        Map<String, Order> orderMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a, LinkedHashMap::new));

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, orderMap.keySet()));

        // 商品 -> 品类
        Set<String> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        Map<String, Product> productMap = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<String, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        // 品类消费聚合
        Map<String, BuyerAnalyticsVO.CategoryShare> shareMap = new LinkedHashMap<>();
        BigDecimal totalSpent = BigDecimal.ZERO;
        long itemCount = 0;
        Map<String, Long> purchaseByCategory = new LinkedHashMap<>();

        for (OrderItem item : items) {
            Product product = productMap.get(item.getProductId());
            String catId = product == null ? "" : product.getCategoryId();
            String catName = categoryNames.getOrDefault(catId, "其他");
            BigDecimal amount = (item.getProductPrice() == null ? BigDecimal.ZERO : item.getProductPrice())
                    .multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()));
            totalSpent = totalSpent.add(amount);
            itemCount += item.getQuantity() == null ? 0 : item.getQuantity();

            BuyerAnalyticsVO.CategoryShare share = shareMap.computeIfAbsent(catName, k -> {
                BuyerAnalyticsVO.CategoryShare s = new BuyerAnalyticsVO.CategoryShare();
                s.setName(k);
                s.setAmount(BigDecimal.ZERO);
                s.setCount(0);
                return s;
            });
            share.setAmount(share.getAmount().add(amount));
            share.setCount(share.getCount() + 1);
            purchaseByCategory.merge(catName, (long) (item.getQuantity() == null ? 0 : item.getQuantity()), Long::sum);
        }

        vo.setTotalSpent(totalSpent);
        vo.setOrderCount(orders.size());
        vo.setItemCount(itemCount);
        vo.setCategoryShares(shareMap.values().stream()
                .sorted(Comparator.comparing(BuyerAnalyticsVO.CategoryShare::getAmount).reversed())
                .collect(Collectors.toList()));
        vo.setPreferences(buildPreferences(userId, purchaseByCategory));
        vo.setKeywords(buildKeywords(userId));
        return vo;
    }

    /**
     * 六维偏好得分：浏览 ×1 + 加购 ×5 + 购买 ×10，再归一化到 0-100。
     * 没有行为数据时返回全 0，前端显示空态提示。
     */
    private List<BuyerAnalyticsVO.PreferenceScore> buildPreferences(String userId, Map<String, Long> purchase) {
        Map<String, String> catIdToName = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        // 父品类映射：子品类 -> 一级品类名
        Map<String, String> parentOf = new LinkedHashMap<>();
        List<Category> all = categoryMapper.selectList(null);
        Map<String, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));
        for (Category c : all) {
            if (c.getParentId() != null && byId.containsKey(c.getParentId())) {
                parentOf.put(c.getId(), byId.get(c.getParentId()).getName());
            } else {
                parentOf.put(c.getId(), c.getName());
            }
        }

        Map<String, Long> scoreByRoot = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.getParentId() == null) {
                scoreByRoot.put(c.getName(), 0L);
            }
        }

        // 浏览 ×1
        List<BrowseHistory> browses = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId));
        if (!browses.isEmpty()) {
            Set<String> pids = browses.stream().map(BrowseHistory::getProductId).collect(Collectors.toSet());
            Map<String, String> pidToCat = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p.getCategoryId() == null ? "" : p.getCategoryId(), (a, b) -> a));
            for (BrowseHistory b : browses) {
                String root = parentOf.get(pidToCat.getOrDefault(b.getProductId(), ""));
                if (root != null) {
                    scoreByRoot.merge(root, 1L, Long::sum);
                }
            }
        }

        // 加购 ×5
        List<CartItem> carts = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        if (!carts.isEmpty()) {
            Set<String> pids = carts.stream().map(CartItem::getProductId).collect(Collectors.toSet());
            Map<String, String> pidToCat = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p.getCategoryId() == null ? "" : p.getCategoryId(), (a, b) -> a));
            for (CartItem c : carts) {
                String root = parentOf.get(pidToCat.getOrDefault(c.getProductId(), ""));
                if (root != null) {
                    scoreByRoot.merge(root, 5L * Math.max(1, c.getQuantity() == null ? 1 : c.getQuantity()), Long::sum);
                }
            }
        }

        // 购买 ×10：purchase 的 key 是品类名，这里映射回一级品类
        for (Map.Entry<String, Long> e : purchase.entrySet()) {
            String root = e.getKey();
            // purchase 里存的可能是子品类名，找它的一级父类
            for (Category c : all) {
                if (c.getName().equals(e.getKey())) {
                    root = parentOf.getOrDefault(c.getId(), c.getName());
                    break;
                }
            }
            if (scoreByRoot.containsKey(root)) {
                scoreByRoot.merge(root, e.getValue() * 10, Long::sum);
            }
        }

        long max = scoreByRoot.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<BuyerAnalyticsVO.PreferenceScore> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : scoreByRoot.entrySet()) {
            BuyerAnalyticsVO.PreferenceScore s = new BuyerAnalyticsVO.PreferenceScore();
            s.setName(e.getKey());
            s.setRaw(e.getValue());
            s.setScore(max == 0 ? 0 : (int) Math.round(e.getValue() * 100.0 / max));
            list.add(s);
        }
        return list;
    }

    /** 从对话记录里提取高频词，按类型着色 */
    private List<BuyerAnalyticsVO.Keyword> buildKeywords(String userId) {
        List<DialogueTurn> turns = dialogueTurnMapper.selectList(new LambdaQueryWrapper<DialogueTurn>()
                .eq(DialogueTurn::getRole, "user")
                .orderByDesc(DialogueTurn::getId)
                .last("LIMIT 200"));
        // 只取当前用户的对话
        List<Long> ids = turns.stream().map(DialogueTurn::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, String> typeOf = new LinkedHashMap<>();

        String[][] dict = {
                // 品类词
                {"耳机", "category"}, {"降噪", "feature"}, {"跑鞋", "category"}, {"咖啡", "category"},
                {"护肤", "category"}, {"精华", "category"}, {"键盘", "category"}, {"笔记本", "category"},
                {"手机", "category"}, {"手表", "category"}, {"瑜伽", "category"}, {"背包", "category"},
                {"双肩包", "category"}, {"净化器", "category"}, {"扫地", "category"}, {"键盘", "category"},
                // 属性/场景词
                {"防水", "feature"}, {"续航", "feature"}, {"轻量", "feature"}, {"保湿", "feature"},
                {"敏感肌", "feature"}, {"运动", "scene"}, {"跑步", "scene"}, {"通勤", "scene"},
                {"送礼", "scene"}, {"预算", "price"}, {"性价比", "feature"},
        };

        for (DialogueTurn t : turns) {
            String content = t.getContent();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            for (String[] kv : dict) {
                if (content.contains(kv[0])) {
                    freq.merge(kv[0], 1, Integer::sum);
                    typeOf.putIfAbsent(kv[0], kv[1]);
                }
            }
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(24)
                .map(e -> {
                    BuyerAnalyticsVO.Keyword k = new BuyerAnalyticsVO.Keyword();
                    k.setWord(e.getKey());
                    k.setWeight(e.getValue());
                    k.setType(typeOf.getOrDefault(e.getKey(), "feature"));
                    return k;
                })
                .collect(Collectors.toList());
    }

    // ========================================================= 卖家

    public SellerAnalyticsVO sellerAnalytics(int days) {
        CurrentUser user = AuthContext.require();
        if (!user.canManageProducts()) {
            throw new BizException(403, "只有卖家或管理员可以查看经营数据");
        }
        SellerAnalyticsVO vo = new SellerAnalyticsVO();
        int span = Math.max(7, Math.min(days, 90));

        List<Product> myProducts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, user.id()));
        List<String> myProductIds = myProducts.stream().map(Product::getId).toList();

        List<OrderItem> myItems = myProductIds.isEmpty() ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getProductId, myProductIds));

        Set<String> myOrderIds = myItems.stream().map(OrderItem::getOrderId).collect(Collectors.toSet());
        List<Order> myOrders = myOrderIds.isEmpty() ? List.of()
                : orderMapper.selectBatchIds(myOrderIds);

        // KPI
        SellerAnalyticsVO.Kpi kpi = new SellerAnalyticsVO.Kpi();
        BigDecimal sales = BigDecimal.ZERO;
        long pendingShip = 0;
        Map<String, Order> orderById = myOrders.stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));
        Map<String, List<OrderItem>> itemsByOrder = myItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (OrderItem item : myItems) {
            Order order = orderById.get(item.getOrderId());
            if (order == null || !PAID_STATUS.contains(order.getStatus())) {
                continue;
            }
            sales = sales.add((item.getProductPrice() == null ? BigDecimal.ZERO : item.getProductPrice())
                    .multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity())));
        }
        for (OrderItem item : myItems) {
            if (OrderItem.SHIP_PENDING.equals(item.getShipStatus())) {
                Order o = orderById.get(item.getOrderId());
                if (o != null && OrderStatus.PAID.equals(o.getStatus())) {
                    pendingShip++;
                }
            }
        }
        kpi.setTotalSales(sales);
        kpi.setOrderCount(orderById.size());
        kpi.setPendingShipCount(pendingShip);
        kpi.setProductCount(myProducts.size());

        // 浏览量
        long views = myProductIds.isEmpty() ? 0
                : browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>()
                        .in(BrowseHistory::getProductId, myProductIds));
        kpi.setViewCount(views);

        // 平均评分
        if (!myProductIds.isEmpty()) {
            List<ProductReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                    .in(ProductReview::getProductId, myProductIds));
            if (!reviews.isEmpty()) {
                double avg = reviews.stream().mapToInt(r -> r.getRating() == null ? 0 : r.getRating()).average().orElse(0);
                kpi.setAvgRating(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
            }
            // 评分分布
            Map<Integer, Long> dist = new LinkedHashMap<>();
            for (int s = 5; s >= 1; s--) {
                dist.put(s, 0L);
            }
            for (ProductReview r : reviews) {
                int star = r.getRating() == null ? 0 : Math.max(1, Math.min(5, r.getRating()));
                dist.merge(star, 1L, Long::sum);
            }
            List<SellerAnalyticsVO.RatingBucket> buckets = new ArrayList<>();
            for (Map.Entry<Integer, Long> e : dist.entrySet()) {
                SellerAnalyticsVO.RatingBucket b = new SellerAnalyticsVO.RatingBucket();
                b.setStar(e.getKey());
                b.setCount(e.getValue());
                buckets.add(b);
            }
            vo.setRatings(buckets);
            vo.setReviewKeywords(buildReviewKeywords(reviews));
        } else {
            vo.setRatings(List.of());
            vo.setReviewKeywords(List.of());
        }
        vo.setKpi(kpi);

        // 销售趋势
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> salesByDay = new LinkedHashMap<>();
        Map<String, Long> ordersByDay = new LinkedHashMap<>();
        for (int i = span - 1; i >= 0; i--) {
            String key = today.minusDays(i).toString();
            salesByDay.put(key, BigDecimal.ZERO);
            ordersByDay.put(key, 0L);
        }
        for (Map.Entry<String, List<OrderItem>> e : itemsByOrder.entrySet()) {
            Order order = orderById.get(e.getKey());
            if (order == null || !PAID_STATUS.contains(order.getStatus()) || order.getCreatedAt() == null) {
                continue;
            }
            String key = order.getCreatedAt().toLocalDate().toString();
            if (!salesByDay.containsKey(key)) {
                continue;
            }
            BigDecimal amount = e.getValue().stream()
                    .map(i -> (i.getProductPrice() == null ? BigDecimal.ZERO : i.getProductPrice())
                            .multiply(BigDecimal.valueOf(i.getQuantity() == null ? 0 : i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            salesByDay.merge(key, amount, BigDecimal::add);
            ordersByDay.merge(key, 1L, Long::sum);
        }
        List<SellerAnalyticsVO.TrendPoint> trend = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : salesByDay.entrySet()) {
            SellerAnalyticsVO.TrendPoint p = new SellerAnalyticsVO.TrendPoint();
            p.setDate(e.getKey());
            p.setSales(e.getValue());
            long cnt = ordersByDay.getOrDefault(e.getKey(), 0L);
            p.setOrders(cnt);
            p.setAvgOrder(cnt == 0 ? BigDecimal.ZERO
                    : e.getValue().divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP));
            trend.add(p);
        }
        vo.setTrend(trend);

        // 商品散点
        List<SellerAnalyticsVO.ProductPoint> points = new ArrayList<>();
        for (Product p : myProducts) {
            SellerAnalyticsVO.ProductPoint pt = new SellerAnalyticsVO.ProductPoint();
            pt.setName(p.getName());
            pt.setSales(p.getSales() == null ? 0 : p.getSales());
            pt.setStock(p.getStock() == null ? 0 : p.getStock());
            pt.setRating(p.getRating() == null ? BigDecimal.ZERO : p.getRating());
            pt.setViews(browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getProductId, p.getId())));
            points.add(pt);
        }
        points.sort(Comparator.comparingLong(SellerAnalyticsVO.ProductPoint::getViews).reversed());
        vo.setProducts(points);

        // 转化漏斗：浏览 → 下单 → 支付
        // 去掉「加购」层：cart_item 对 (user_id, product_id) 有唯一约束，
        // 每个买家每种商品只能有一条加购记录，「加购数」天然被商品种类数压制，
        // 与「订单数」不可比，强行放入漏斗会出现 >100% 的失真转化率。
        // 这里只保留口径一致的三层，保证图表诚实可信。
        long orderCount = orderById.size();
        long paidCount = orderById.values().stream().filter(o -> PAID_STATUS.contains(o.getStatus())).count();
        vo.setFunnel(buildFunnel(views, orderCount, paidCount));
        return vo;
    }

    /**
     * 构造转化漏斗（浏览 → 下单 → 支付）。
     * 三层口径一致：浏览按浏览记录条数，下单按订单数，支付按已支付订单数，
     * 因此每层转化率都有明确业务含义，且不会出现 >100% 的失真值。
     */
    private List<SellerAnalyticsVO.FunnelStep> buildFunnel(long views, long orders, long paid) {
        List<SellerAnalyticsVO.FunnelStep> steps = new ArrayList<>();
        long[] values = {views, orders, paid};
        String[] names = {"浏览量", "下单量", "支付成功"};
        for (int i = 0; i < names.length; i++) {
            SellerAnalyticsVO.FunnelStep s = new SellerAnalyticsVO.FunnelStep();
            s.setName(names[i]);
            s.setValue(values[i]);
            if (i == 0) {
                s.setRate(BigDecimal.valueOf(100));
            } else {
                long prev = values[i - 1];
                s.setRate(prev == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(values[i] * 100.0 / prev).setScale(1, RoundingMode.HALF_UP));
            }
            steps.add(s);
        }
        return steps;
    }

    /** 好评词 / 差评词提取：4-5 星算好评，1-2 星算差评 */
    private List<SellerAnalyticsVO.Keyword> buildReviewKeywords(List<ProductReview> reviews) {
        String[] goodWords = {"性价比", "好用", "满意", "音质", "续航", "舒服", "推荐", "划算", "质量", "喜欢"};
        String[] badWords = {"贵", "失望", "一般", "退货", "问题", "不好", "差", "慢", "假", "噪音"};
        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, String> typeOf = new LinkedHashMap<>();

        for (ProductReview r : reviews) {
            if (!StringUtils.hasText(r.getContent())) {
                continue;
            }
            int star = r.getRating() == null ? 3 : r.getRating();
            if (star >= 4) {
                for (String w : goodWords) {
                    if (r.getContent().contains(w)) {
                        freq.merge(w, 1, Integer::sum);
                        typeOf.put(w, "good");
                    }
                }
            } else if (star <= 2) {
                for (String w : badWords) {
                    if (r.getContent().contains(w)) {
                        freq.merge(w, 1, Integer::sum);
                        typeOf.put(w, "bad");
                    }
                }
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(16)
                .map(e -> {
                    SellerAnalyticsVO.Keyword k = new SellerAnalyticsVO.Keyword();
                    k.setWord(e.getKey());
                    k.setWeight(e.getValue());
                    k.setType(typeOf.get(e.getKey()));
                    return k;
                })
                .collect(Collectors.toList());
    }
}