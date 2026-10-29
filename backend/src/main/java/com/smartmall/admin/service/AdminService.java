package com.smartmall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.admin.dto.AdminCategoryRequest;
import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.admin.dto.DashboardVO;
import com.smartmall.admin.dto.AdminConversationVO;
import com.smartmall.admin.dto.ReviewAdminVO;
import com.smartmall.agent.entity.Conversation;
import com.smartmall.agent.mapper.AgentExecutionLogMapper;
import com.smartmall.agent.mapper.ConversationMapper;
import com.smartmall.agent.mapper.DialogueTurnMapper;
import com.smartmall.agent.entity.DialogueTurn;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import com.smartmall.common.PageParams;
import com.smartmall.common.PageResult;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.Order;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Category;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.entity.ProductReview;
import com.smartmall.product.mapper.ProductReviewMapper;
import com.smartmall.product.service.ProductService;
import com.smartmall.user.dto.UserVO;
import com.smartmall.user.entity.SysUser;
import com.smartmall.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ProductMapper productMapper;
    private final ProductReviewMapper reviewMapper;
    private final CategoryMapper categoryMapper;
    private final ConversationMapper conversationMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final SysUserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AgentExecutionLogMapper logMapper;
    private final ProductService productService;
    private final OrderService orderService;

    public PageResult<ProductVO> products(String keyword, String categoryId, Integer status,
                                          long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .and(StringUtils.hasText(keyword), w -> w.like(Product::getName, keyword)
                        .or().like(Product::getBrand, keyword))
                .eq(StringUtils.hasText(categoryId), Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        Page<Product> page = productMapper.selectPage(new Page<>(pageNo, size), wrapper);
        Map<String, Category> categories = productService.categoryMap();
        List<ProductVO> records = page.getRecords().stream()
                .map(p -> productService.toVO(p, categories, null, null)).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public java.util.Map<String, Object> importProducts(java.util.List<AdminProductRequest> requests) {
        int success = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            try {
                createProduct(requests.get(i));
                success++;
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 条：" + e.getMessage());
            }
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    public ProductVO createProduct(AdminProductRequest request) {
        Product product = new Product();
        product.setId(Ids.next());
        apply(product, request);
        product.setSales(0);
        product.setRating(BigDecimal.valueOf(5.0));
        product.setReviewCount(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    public ProductVO updateProduct(String id, AdminProductRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        apply(product, request);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    @Transactional
    public void deleteProduct(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        product.setStatus(0);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
    }

    public void updateProductStatus(String id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        product.setStatus(status == null || status != 1 ? 0 : 1);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
    }

    public List<Category> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getName));
    }

    @Transactional
    public Category createCategory(AdminCategoryRequest request) {
        Category category = new Category();
        category.setId(Ids.next());
        applyCategory(category, request);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public Category updateCategory(String id, AdminCategoryRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        if (id.equals(request.getParentId())) {
            throw new BizException("分类不能作为自己的父分类");
        }
        applyCategory(category, request);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public void deleteCategory(String id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException("请先删除或移动该分类下的子分类");
        }
        Long products = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (products != null && products > 0) {
            throw new BizException("该分类下还有商品，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    private void applyCategory(Category category, AdminCategoryRequest request) {
        category.setName(request.getName().trim());
        category.setParentId(StringUtils.hasText(request.getParentId()) ? request.getParentId() : null);
        category.setIcon(request.getIcon());
        category.setDescription(request.getDescription());
        category.setSortOrder(request.getSortOrder() == null ? 10 : request.getSortOrder());
        category.setStatus(request.getStatus() == null || request.getStatus() == 1 ? 1 : 0);
    }

    public PageResult<ReviewAdminVO> reviews(Integer status, String keyword, long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        Page<ProductReview> page = reviewMapper.selectPage(
                new Page<>(pageNo, size),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(status != null, ProductReview::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w.like(ProductReview::getContent, keyword)
                                .or().like(ProductReview::getUsername, keyword))
                        .orderByDesc(ProductReview::getCreatedAt));
        Map<String, Product> products = page.getRecords().isEmpty() ? Map.of()
                : productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .in(Product::getId, page.getRecords().stream().map(ProductReview::getProductId).toList()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<ReviewAdminVO> vos = page.getRecords().stream().map(review -> {
            ReviewAdminVO vo = new ReviewAdminVO();
            vo.setId(review.getId());
            vo.setProductId(review.getProductId());
            Product product = products.get(review.getProductId());
            vo.setProductName(product == null ? review.getProductId() : product.getName());
            vo.setUserId(review.getUserId());
            vo.setUsername(review.getUsername());
            vo.setRating(review.getRating());
            vo.setContent(review.getContent());
            vo.setStatus(review.getStatus());
            vo.setCreatedAt(review.getCreatedAt());
            return vo;
        }).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public void updateReviewStatus(Long id, Integer status) {
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw BizException.notFound("评价不存在");
        }
        review.setStatus(status != null && status == 1 ? 1 : 0);
        reviewMapper.updateById(review);
        List<ProductReview> approved = reviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getProductId, review.getProductId())
                .eq(ProductReview::getStatus, 1));
        Product product = productMapper.selectById(review.getProductId());
        if (product != null) {
            product.setReviewCount(approved.size());
            if (approved.isEmpty()) {
                product.setRating(BigDecimal.valueOf(5.0));
            } else {
                BigDecimal sum = approved.stream().map(item -> BigDecimal.valueOf(item.getRating()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                product.setRating(sum.divide(BigDecimal.valueOf(approved.size()), 1, java.math.RoundingMode.HALF_UP));
            }
            productMapper.updateById(product);
        }
    }

    public PageResult<AdminConversationVO> conversations(String keyword, long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        Page<Conversation> page = conversationMapper.selectPage(
                new Page<>(pageNo, size),
                new LambdaQueryWrapper<Conversation>()
                        .like(StringUtils.hasText(keyword), Conversation::getTitle, keyword)
                        .orderByDesc(Conversation::getUpdatedAt));
        Map<String, SysUser> users = page.getRecords().isEmpty() ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, page.getRecords().stream().map(Conversation::getUserId).distinct().toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        List<AdminConversationVO> vos = page.getRecords().stream().map(conversation -> {
            AdminConversationVO vo = new AdminConversationVO();
            vo.setId(conversation.getId());
            vo.setUserId(conversation.getUserId());
            SysUser user = users.get(conversation.getUserId());
            vo.setUsername(user == null ? conversation.getUserId() : user.getUsername());
            vo.setNickname(user == null ? null : user.getNickname());
            vo.setTitle(conversation.getTitle());
            Long count = dialogueTurnMapper.selectCount(new LambdaQueryWrapper<DialogueTurn>()
                    .eq(DialogueTurn::getConversationId, conversation.getId())
                    .eq(DialogueTurn::getRole, "user"));
            vo.setTurnCount(count == null ? 0 : count.intValue());
            vo.setCreatedAt(conversation.getCreatedAt());
            vo.setUpdatedAt(conversation.getUpdatedAt());
            return vo;
        }).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<UserVO> users(String keyword, long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNo, size),
                new LambdaQueryWrapper<SysUser>()
                        .and(StringUtils.hasText(keyword), w -> w.like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getNickname, keyword)
                                .or().like(SysUser::getEmail, keyword))
                        .orderByDesc(SysUser::getCreatedAt));
        return PageResult.of(page.convert(UserVO::from));
    }

    public void updateUserStatus(String id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BizException("不能禁用管理员账号");
        }
        user.setStatus(status == null || status != 1 ? 0 : 1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public DashboardVO dashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setProductCount(productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
        vo.setOrderCount(orderMapper.selectCount(null));
        vo.setAgentLogCount(logMapper.selectCount(null));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        vo.setTodayOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, todayStart)));

        List<Order> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED));
        BigDecimal totalSales = paidOrders.stream().map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todaySales = paidOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(todayStart))
                .map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalSales(totalSales);
        vo.setTodaySales(todaySales);

        for (String status : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED,
                OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.CLOSED)) {
            long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStatus, status));
            vo.getOrderStatus().add(new DashboardVO.StatusCount(status, OrderStatus.label(status), count));
        }

        Map<String, Category> categories = productService.categoryMap();
        List<Product> top = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1).orderByDesc(Product::getSales).last("LIMIT 5"));
        vo.setTopProducts(top.stream().map(p -> productService.toVO(p, categories, null, null)).toList());
        vo.setRecentOrders(orderService.adminList(null, null, 1, 5).getRecords());

        LocalDate startDate = LocalDate.now().minusDays(6);
        List<Order> recent = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, startDate.atStartOfDay())
                .in(Order::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED));
        Map<String, List<Order>> byDate = recent.stream().collect(Collectors.groupingBy(
                o -> o.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd"))));
        for (int i = 0; i < 7; i++) {
            String date = startDate.plusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
            List<Order> orders = byDate.getOrDefault(date, List.of());
            BigDecimal amount = orders.stream().map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.getTrend().add(new DashboardVO.DailyPoint(date, orders.size(), amount));
        }
        return vo;
    }

    private void apply(Product product, AdminProductRequest request) {
        product.setName(request.getName());
        product.setSubtitle(request.getSubtitle());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice() == null ? request.getPrice() : request.getOriginalPrice());
        product.setStock(request.getStock() == null ? 0 : request.getStock());
        product.setDescription(request.getDescription());
        product.setAttributes(JsonUtils.toJson(request.getAttributes() == null ? new LinkedHashMap<>() : request.getAttributes()));
        product.setTags(request.getTags());
        product.setImage(StringUtils.hasText(request.getImage()) ? request.getImage() : "/img/default.svg");
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}
