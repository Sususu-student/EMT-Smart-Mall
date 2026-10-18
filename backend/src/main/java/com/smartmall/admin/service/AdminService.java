package com.smartmall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.admin.dto.DashboardVO;
import com.smartmall.agent.mapper.AgentExecutionLogMapper;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.JsonUtils;
import com.smartmall.common.PageResult;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.Order;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.order.service.OrderService;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Category;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.CategoryMapper;
import com.smartmall.product.mapper.ProductMapper;
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
    private final CategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AgentExecutionLogMapper logMapper;
    private final ProductService productService;
    private final OrderService orderService;

    public PageResult<ProductVO> products(String keyword, String categoryId, Integer status,
                                          long pageNo, long size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .and(StringUtils.hasText(keyword), w -> w.like(Product::getName, keyword)
                        .or().like(Product::getBrand, keyword))
                .eq(StringUtils.hasText(categoryId), Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        Page<Product> page = productMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, size))), wrapper);
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

    public PageResult<UserVO> users(String keyword, long pageNo, long size) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, size))),
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
