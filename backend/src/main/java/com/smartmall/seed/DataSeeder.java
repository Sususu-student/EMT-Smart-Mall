package com.smartmall.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmall.cart.entity.CartItem;
import com.smartmall.cart.mapper.CartItemMapper;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.Order;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.entity.ProductImage;
import com.smartmall.product.entity.ProductReview;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductImageMapper;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.mapper.ProductReviewMapper;
import com.smartmall.user.entity.BrowseHistory;
import com.smartmall.user.entity.Favorite;
import com.smartmall.user.entity.SysUser;
import com.smartmall.user.entity.UserAddress;
import com.smartmall.user.entity.UserPreference;
import com.smartmall.user.mapper.BrowseHistoryMapper;
import com.smartmall.user.mapper.FavoriteMapper;
import com.smartmall.user.mapper.SysUserMapper;
import com.smartmall.user.mapper.UserAddressMapper;
import com.smartmall.user.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductReviewMapper reviewMapper;
    private final SysUserMapper userMapper;
    private final UserAddressMapper addressMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PasswordEncoder passwordEncoder;

    private static final String[] REVIEW_USERS = {"拾光旅人", "山间清风", "一只小柚子", "数字游民", "晨跑的猫", "南风知我意"};
    private static final String[] REVIEW_TEXTS = {
            "实物和描述一致，做工比预期好，用了一周很满意。",
            "物流很快，包装完整，参数标注真实，性价比不错。",
            "对比了好几款最后选了这个，日常使用完全够用。",
            "客服响应及时，细节处理到位，会推荐给朋友。",
            "整体体验不错，唯一的小遗憾是颜色选择少了点。",
            "第二次回购了，品质稳定，值得入手。"
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (productMapper.selectCount(null) > 0) {
            log.info("检测到已有商品数据，跳过种子数据初始化");
            seedUsersIfAbsent();
            return;
        }
        CatalogSeed seed;
        try (InputStream in = new ClassPathResource("seed/catalog.json").getInputStream()) {
            seed = new ObjectMapper().readValue(in, CatalogSeed.class);
        }
        if (seed == null || seed.getProducts() == null) {
            log.warn("未找到 seed/catalog.json，跳过初始化");
            return;
        }

        Map<String, String> categoryNames = new java.util.HashMap<>();
        for (CatalogSeed.SeedCategory c : seed.getCategories()) {
            Category category = new Category();
            category.setId(c.getId());
            category.setParentId(c.getParentId());
            category.setName(c.getName());
            category.setIcon(c.getIcon());
            category.setDescription(c.getDescription());
            category.setAttributeSchema(c.getAttributeSchema() == null ? null : JsonUtils.toJson(c.getAttributeSchema()));
            category.setSortOrder(c.getSortOrder() == null ? 0 : c.getSortOrder());
            category.setStatus(1);
            categoryMapper.insert(category);
            categoryNames.put(c.getId(), c.getName());
        }

        LocalDateTime now = LocalDateTime.now();
        for (CatalogSeed.SeedProduct sp : seed.getProducts()) {
            Product product = new Product();
            product.setId(sp.getId());
            product.setName(sp.getName());
            product.setSubtitle(sp.getSubtitle());
            product.setCategoryId(sp.getCategoryId());
            product.setBrand(sp.getBrand());
            product.setPrice(sp.getPrice());
            product.setOriginalPrice(sp.getOriginalPrice());
            product.setStock(sp.getStock());
            product.setSales(sp.getSales());
            product.setRating(sp.getRating());
            product.setReviewCount(sp.getReviewCount());
            product.setDescription(sp.getDescription());
            product.setAttributes(JsonUtils.toJson(sp.getAttributes()));
            product.setTags(sp.getTags());
            product.setImage(sp.getImage());
            product.setStatus(sp.getStatus() == null ? 1 : sp.getStatus());
            product.setCreatedAt(now.minusDays(ThreadLocalRandom.current().nextInt(1, 120)));
            product.setUpdatedAt(now);
            productMapper.insert(product);

            ProductImage image = new ProductImage();
            image.setProductId(product.getId());
            image.setUrl(sp.getImage());
            image.setSortOrder(0);
            productImageMapper.insert(image);

            int reviewCount = 0;
            for (int i = 0; i < 5; i++) {
                ProductReview review = new ProductReview();
                review.setProductId(product.getId());
                review.setUserId("seed_reviewer_" + i);
                review.setUsername(REVIEW_USERS[(i + product.getId().length()) % REVIEW_USERS.length]);
                review.setRating(i == 0 ? 5 : 4 + (i % 2));
                review.setContent(REVIEW_TEXTS[(i + product.getId().length()) % REVIEW_TEXTS.length]);
                review.setCreatedAt(now.minusDays(ThreadLocalRandom.current().nextInt(1, 90)));
                reviewMapper.insert(review);
                reviewCount++;
            }
            // 保留商品数据里的评分分布，评价列表展示 5 条代表性评价
            product.setReviewCount(reviewCount);
            productMapper.updateById(product);
        }

        seedDemoData(seed, now);
        log.info("种子数据初始化完成：{} 个分类，{} 件商品", seed.getCategories().size(), seed.getProducts().size());
    }

    private void seedUsersIfAbsent() {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin")) == 0) {
            createUser("u_admin", "admin", "Admin@123", "平台管理员", "admin@smartmall.dev", "ADMIN");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "demo")) == 0) {
            createUser("u_demo", "demo", "Demo@123", "体验用户", "demo@smartmall.dev", "USER");
        }
    }

    private void seedDemoData(CatalogSeed seed, LocalDateTime now) {
        seedUsersIfAbsent();

        if (addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, "u_demo")) == 0) {
            UserAddress address = new UserAddress();
            address.setId("addr_demo");
            address.setUserId("u_demo");
            address.setReceiver("苏同学");
            address.setPhone("13800138000");
            address.setProvince("江苏省");
            address.setCity("南京市");
            address.setDistrict("栖霞区");
            address.setDetail("文苑路 1 号 智能导购实验室");
            address.setIsDefault(1);
            address.setCreatedAt(now);
            addressMapper.insert(address);
        }

        if (preferenceMapper.selectCount(new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, "u_demo")) == 0) {
            preference("u_demo", "品类", "蓝牙耳机", "1.00", now);
            preference("u_demo", "预算", "300", "0.80", now);
            preference("u_demo", "场景", "跑步", "0.90", now);
            preference("u_demo", "品牌", "声屿", "0.60", now);
        }

        if (favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, "u_demo")) == 0) {
            favorite("u_demo", "p_headphone_02", now);
            favorite("u_demo", "p_running_04", now);
        }

        if (browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>().eq(BrowseHistory::getUserId, "u_demo")) == 0) {
            for (String id : List.of("p_headphone_01", "p_headphone_02", "p_watch_03", "p_running_02", "p_coffee_01")) {
                BrowseHistory history = new BrowseHistory();
                history.setUserId("u_demo");
                history.setProductId(id);
                history.setCreatedAt(now.minusHours(ThreadLocalRandom.current().nextInt(1, 48)));
                browseHistoryMapper.insert(history);
            }
        }

        if (cartItemMapper.selectCount(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, "u_demo")) == 0) {
            CartItem item = new CartItem();
            item.setUserId("u_demo");
            item.setProductId("p_beans_02");
            item.setQuantity(2);
            item.setSelected(1);
            item.setCreatedAt(now.minusHours(3));
            item.setUpdatedAt(now.minusHours(3));
            cartItemMapper.insert(item);
        }

        if (orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getUserId, "u_demo")) == 0) {
            Product headphone = productMapper.selectById("p_headphone_02");
            Product beans = productMapper.selectById("p_beans_02");
            if (headphone != null) {
                createOrder("o_demo_01", "SG2026031509300001", headphone, 1, OrderStatus.COMPLETED,
                        now.minusDays(18), now.minusDays(18), now.minusDays(17), now.minusDays(14));
            }
            if (beans != null) {
                createOrder("o_demo_02", "SG2026041710150002", beans, 2, OrderStatus.PAID,
                        now.minusDays(2), now.minusDays(2), null, null);
            }
        }
    }

    private void createUser(String id, String username, String rawPassword, String nickname, String email, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setAvatar(null);
        user.setRole(role);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
    }

    private void preference(String userId, String key, String value, String weight, LocalDateTime now) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setPreferenceKey(key);
        preference.setPreferenceValue(value);
        preference.setWeight(new BigDecimal(weight));
        preference.setUpdatedAt(now);
        preferenceMapper.insert(preference);
    }

    private void favorite(String userId, String productId, LocalDateTime now) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreatedAt(now.minusDays(ThreadLocalRandom.current().nextInt(1, 15)));
        favoriteMapper.insert(favorite);
    }

    private void createOrder(String id, String orderNo, Product product, int quantity, String status,
                             LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                             LocalDateTime finishedAt) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId("u_demo");
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(status);
        order.setAddressId("addr_demo");
        order.setReceiver("苏同学");
        order.setPhone("13800138000");
        order.setAddressText("江苏省南京市栖霞区文苑路 1 号 智能导购实验室");
        order.setRemark("演示订单");
        order.setCreatedAt(createdAt);
        order.setPaidAt(paidAt);
        order.setShippedAt(shippedAt);
        order.setFinishedAt(finishedAt);
        order.setExpiredAt(createdAt.plusMinutes(30));
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(id);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage(product.getImage());
        item.setProductPrice(product.getPrice());
        item.setQuantity(quantity);
        orderItemMapper.insert(item);
    }

    @SuppressWarnings("unused")
    private String newId() {
        return Ids.next();
    }
}
