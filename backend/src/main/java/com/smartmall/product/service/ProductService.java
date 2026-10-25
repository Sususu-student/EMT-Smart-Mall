package com.smartmall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.auth.AuthContext;
import com.smartmall.common.BizException;
import com.smartmall.common.JsonUtils;
import com.smartmall.common.PageResult;
import com.smartmall.product.dto.CategoryVO;
import com.smartmall.product.dto.ProductQuery;
import com.smartmall.product.dto.ProductVO;
import com.smartmall.product.dto.ReviewRequest;
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
import com.smartmall.user.mapper.BrowseHistoryMapper;
import com.smartmall.user.mapper.FavoriteMapper;
import com.smartmall.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductReviewMapper reviewMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final SysUserMapper userMapper;

    public PageResult<ProductVO> list(ProductQuery query, String userId) {
        query.normalize();
        List<String> categoryIds = StringUtils.hasText(query.getCategoryId())
                ? descendantIds(query.getCategoryId()) : List.of();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1);
        if (!categoryIds.isEmpty()) {
            wrapper.in(Product::getCategoryId, categoryIds);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or().like(Product::getSubtitle, keyword)
                    .or().like(Product::getBrand, keyword)
                    .or().like(Product::getTags, keyword));
        }
        if (query.getMinPrice() != null) {
            wrapper.ge(Product::getPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, query.getMaxPrice());
        }
        if (StringUtils.hasText(query.getBrand())) {
            wrapper.eq(Product::getBrand, query.getBrand());
        }
        switch (query.getSort() == null ? "default" : query.getSort()) {
            case "price_asc" -> wrapper.orderByAsc(Product::getPrice);
            case "price_desc" -> wrapper.orderByDesc(Product::getPrice);
            case "sales" -> wrapper.orderByDesc(Product::getSales);
            case "rating" -> wrapper.orderByDesc(Product::getRating);
            case "new" -> wrapper.orderByDesc(Product::getCreatedAt);
            default -> wrapper.orderByDesc(Product::getSales).orderByDesc(Product::getRating);
        }

        Page<Product> page = productMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<String, Category> categories = categoryMap();
        List<ProductVO> records = page.getRecords().stream()
                .map(p -> toVO(p, categories, null, isFavorite(userId, p.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public ProductVO detail(String id, String userId) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw BizException.notFound("商品不存在或已下架");
        }
        List<String> images = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, id).orderByAsc(ProductImage::getSortOrder))
                .stream().map(ProductImage::getUrl).toList();
        if (StringUtils.hasText(userId)) {
            BrowseHistory history = new BrowseHistory();
            history.setUserId(userId);
            history.setProductId(id);
            history.setCreatedAt(LocalDateTime.now());
            browseHistoryMapper.insert(history);
        }
        return toVO(product, categoryMap(), images, isFavorite(userId, id));
    }

    public List<ProductVO> related(String id, int limit) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return List.of();
        }
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, product.getCategoryId())
                .eq(Product::getStatus, 1)
                .ne(Product::getId, id)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + Math.max(1, limit)));
        Map<String, Category> categories = categoryMap();
        return products.stream().map(p -> toVO(p, categories, null, null)).toList();
    }

    public List<CategoryVO> categoryTree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1).orderByAsc(Category::getSortOrder));
        Map<String, CategoryVO> index = new LinkedHashMap<>();
        all.forEach(c -> index.put(c.getId(), toCategoryVO(c)));
        List<CategoryVO> roots = new ArrayList<>();
        for (Category category : all) {
            CategoryVO vo = index.get(category.getId());
            if (StringUtils.hasText(category.getParentId()) && index.containsKey(category.getParentId())) {
                index.get(category.getParentId()).getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    public List<String> descendantIds(String categoryId) {
        List<Category> all = categoryMapper.selectList(null);
        List<String> ids = new ArrayList<>();
        if (all.stream().noneMatch(c -> c.getId().equals(categoryId))) {
            return ids;
        }
        ids.add(categoryId);
        boolean added = true;
        while (added) {
            added = false;
            for (Category c : all) {
                if (c.getParentId() != null && ids.contains(c.getParentId()) && !ids.contains(c.getId())) {
                    ids.add(c.getId());
                    added = true;
                }
            }
        }
        return ids;
    }

    public PageResult<ProductReview> reviews(String productId, long pageNo, long size) {
        Page<ProductReview> page = reviewMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, size))),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreatedAt));
        return PageResult.of(page);
    }

    @Transactional
    public ProductReview addReview(String productId, ReviewRequest request) {
        String userId = AuthContext.require().id();
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getProductId, productId)
                .eq(ProductReview::getUserId, userId));
        if (exists != null && exists > 0) {
            throw new BizException("你已经评价过该商品");
        }
        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setUsername(AuthContext.require().username());
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setStatus(1);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        List<ProductReview> all = reviewMapper.selectList(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getProductId, productId)
                .eq(ProductReview::getStatus, 1));
        BigDecimal avg = all.stream().map(r -> BigDecimal.valueOf(r.getRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(all.size()), 1, RoundingMode.HALF_UP);
        product.setRating(avg);
        product.setReviewCount(all.size());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return review;
    }

    public Map<String, Category> categoryMap() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));
    }

    public ProductVO toVO(Product product, Map<String, Category> categories, List<String> images, Boolean favorited) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setCategoryId(product.getCategoryId());
        Category category = categories.get(product.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        vo.setBrand(product.getBrand());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setReviewCount(product.getReviewCount());
        vo.setDescription(product.getDescription());
        vo.setAttributes(JsonUtils.toMap(product.getAttributes()));
        vo.setAttributeSchema(category == null ? List.of() : JsonUtils.toMapList(category.getAttributeSchema()));
        vo.setTags(product.getTags() == null ? List.of()
                : List.of(product.getTags().split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        vo.setImage(product.getImage());
        vo.setImageFallback(product.getImageFallback());
        vo.setSellerId(product.getSellerId());
        vo.setSellerName(resolveSellerName(product.getSellerId()));
        vo.setImages(images == null || images.isEmpty() ? List.of(product.getImage()) : images);
        vo.setFavorited(favorited);
        vo.setStatus(product.getStatus());
        vo.setCreatedAt(product.getCreatedAt());
        return vo;
    }

    /** 卖家昵称展示；平台自营商品（sellerId 为空）返回固定文案 */
    private String resolveSellerName(String sellerId) {
        if (!StringUtils.hasText(sellerId)) {
            return "智选自营";
        }
        var user = userMapper.selectById(sellerId);
        if (user == null) {
            return "智选自营";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private boolean isFavorite(String userId, String productId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }

    private CategoryVO toCategoryVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setDescription(category.getDescription());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }

    public List<ProductVO> byIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, ids));
        Map<String, Category> categories = categoryMap();
        Map<String, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        return ids.stream().map(byId::get).filter(p -> p != null)
                .sorted(Comparator.comparing(p -> ids.indexOf(p.getId())))
                .map(p -> toVO(p, categories, null, null)).toList();
    }
}
