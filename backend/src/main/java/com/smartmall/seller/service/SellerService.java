package com.smartmall.seller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.admin.dto.AdminProductRequest;
import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.PageResult;
import com.smartmall.order.OrderStatus;
import com.smartmall.order.entity.OrderItem;
import com.smartmall.order.mapper.OrderItemMapper;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import com.smartmall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卖家中心：卖家只能发布和修改自己的商品。
 * 管理员走 AdminService，这里是 SELLER 角色的自服务通道，所有写操作都校验 seller_id 归属。
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private final ProductMapper productMapper;
    private final ProductService productService;
    private final OrderItemMapper orderItemMapper;

    /** 卖家商品列表：只返回自己发布的商品 */
    public PageResult<ProductVO> myProducts(long pageNo, long size, String keyword, Integer status) {
        CurrentUser user = currentSeller();
        Page<Product> page = productMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, size))),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getSellerId, user.id())
                        .like(StringUtils.hasText(keyword), Product::getName, keyword)
                        .eq(status != null, Product::getStatus, status)
                        .orderByDesc(Product::getCreatedAt));
        Map<String, com.smartmall.product.entity.Category> categories = productService.categoryMap();
        List<ProductVO> records = page.getRecords().stream()
                .map(p -> productService.toVO(p, categories, null, null))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public ProductVO publish(AdminProductRequest request) {
        CurrentUser user = currentSeller();
        Product product = new Product();
        product.setId(Ids.next());
        apply(product, request);
        product.setSellerId(user.id());
        product.setSales(0);
        product.setRating(BigDecimal.valueOf(5.0));
        product.setReviewCount(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    @Transactional
    public ProductVO update(String id, AdminProductRequest request) {
        Product product = requireOwnProduct(id);
        apply(product, request);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    /** 上下架：卖家只能操作自己的商品 */
    @Transactional
    public ProductVO toggleStatus(String id, Integer status) {
        Product product = requireOwnProduct(id);
        product.setStatus(status != null && status == 0 ? 0 : 1);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return productService.toVO(product, productService.categoryMap(), null, null);
    }

    /**
     * 删除商品：只允许删除自己的、且没有产生过订单的商品。
     * 有订单的商品直接删除会破坏历史数据，改为提示下架。
     */
    @Transactional
    public void delete(String id) {
        Product product = requireOwnProduct(id);
        Long sold = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getProductId, id));
        if (sold != null && sold > 0) {
            throw new BizException("该商品已产生订单，不能删除，请改为下架");
        }
        productMapper.deleteById(product.getId());
    }

    /** 卖家概览：商品数、在售数、已售件数 */
    public Map<String, Object> overview() {
        CurrentUser user = currentSeller();
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, user.id()));
        long onSale = products.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
        long sold = products.stream().mapToLong(p -> p.getSales() == null ? 0 : p.getSales()).sum();
        BigDecimal stock = products.stream()
                .map(p -> BigDecimal.valueOf(p.getStock() == null ? 0 : p.getStock()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", products.size());
        result.put("onSaleCount", onSale);
        result.put("soldCount", sold);
        result.put("stockTotal", stock);
        return result;
    }

    /** 卖家最近订单：自己商品产生的订单条目 */
    public List<Map<String, Object>> recentOrders(int limit) {
        CurrentUser user = currentSeller();
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, user.id()));
        List<String> productIds = products.stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getProductId, productIds)
                .orderByDesc(OrderItem::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        Map<String, Product> productMap = new LinkedHashMap<>();
        products.forEach(p -> productMap.put(p.getId(), p));

        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderItem item : items) {
            Product product = productMap.get(item.getProductId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", item.getOrderId());
            row.put("productId", item.getProductId());
            row.put("productName", product == null ? item.getProductId() : product.getName());
            row.put("quantity", item.getQuantity());
            row.put("amount", item.getProductPrice() == null ? BigDecimal.ZERO
                    : item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            result.add(row);
        }
        return result;
    }

    private CurrentUser currentSeller() {
        CurrentUser user = AuthContext.require();
        if (!user.isSeller() && !user.isAdmin()) {
            throw new BizException(403, "当前账号不是卖家，无法进入卖家中心");
        }
        return user;
    }

    /** 归属校验：商品必须存在且属于当前卖家（管理员可越过归属限制） */
    private Product requireOwnProduct(String id) {
        CurrentUser user = currentSeller();
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!user.isAdmin() && !user.id().equals(product.getSellerId())) {
            throw new BizException(403, "只能管理自己发布的商品");
        }
        return product;
    }

    private void apply(Product product, AdminProductRequest request) {
        product.setName(request.getName());
        product.setSubtitle(request.getSubtitle());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock() == null ? 0 : request.getStock());
        product.setDescription(request.getDescription());
        product.setAttributes(com.smartmall.common.JsonUtils.toJson(
                request.getAttributes() == null ? new LinkedHashMap<>() : request.getAttributes()));
        product.setTags(request.getTags());
        // 卖家没有上传能力，未填图片时用品类占位图，避免空图
        product.setImage(StringUtils.hasText(request.getImage())
                ? request.getImage()
                : "/img/default.svg");
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}