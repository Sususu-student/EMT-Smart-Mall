package com.smartmall.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.auth.AuthContext;
import com.smartmall.cart.dto.AddCartRequest;
import com.smartmall.cart.dto.CartItemVO;
import com.smartmall.cart.dto.CartSelectRequest;
import com.smartmall.cart.dto.CartSummaryVO;
import com.smartmall.cart.dto.UpdateQuantityRequest;
import com.smartmall.cart.entity.CartItem;
import com.smartmall.cart.mapper.CartItemMapper;
import com.smartmall.common.BizException;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public CartSummaryVO summary() {
        String userId = AuthContext.userId();
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).orderByDesc(CartItem::getCreatedAt));
        List<String> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<String, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, productIds))
                .stream().collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        List<CartItemVO> vos = items.stream().map(item -> toVO(item, products.get(item.getProductId()))).toList();
        CartSummaryVO summary = new CartSummaryVO();
        summary.setItems(vos);
        summary.setTotalQuantity(vos.stream().mapToLong(v -> v.getAvailable() ? v.getQuantity() : 0).sum());
        summary.setSelectedCount((int) vos.stream().filter(v -> v.getAvailable() && v.getSelected() == 1).count());
        summary.setTotalAmount(vos.stream().filter(v -> v.getAvailable() && v.getSelected() == 1)
                .map(CartItemVO::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setSavedAmount(vos.stream().filter(v -> v.getAvailable() && v.getSelected() == 1
                        && v.getOriginalPrice() != null)
                .map(v -> v.getOriginalPrice().subtract(v.getPrice()).multiply(BigDecimal.valueOf(v.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return summary;
    }

    @Transactional
    public CartItemVO add(AddCartRequest request) {
        String userId = AuthContext.userId();
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw BizException.notFound("商品不存在或已下架");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getProductId, request.getProductId()));
        if (existing != null) {
            int total = existing.getQuantity() + quantity;
            if (total > product.getStock()) {
                throw new BizException("库存不足，当前仅剩 " + product.getStock() + " 件");
            }
            existing.setQuantity(total);
            existing.setSelected(1);
            existing.setUpdatedAt(LocalDateTime.now());
            cartItemMapper.updateById(existing);
            return toVO(existing, product);
        }
        if (quantity > product.getStock()) {
            throw new BizException("库存不足，当前仅剩 " + product.getStock() + " 件");
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(request.getProductId());
        item.setQuantity(quantity);
        item.setSelected(1);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        cartItemMapper.insert(item);
        return toVO(item, product);
    }

    public CartItemVO updateQuantity(Long id, UpdateQuantityRequest request) {
        CartItem item = requireOwn(id);
        Product product = productMapper.selectById(item.getProductId());
        if (product == null) {
            throw BizException.notFound("商品已下架");
        }
        if (request.getQuantity() > product.getStock()) {
            throw new BizException("库存不足，当前仅剩 " + product.getStock() + " 件");
        }
        item.setQuantity(request.getQuantity());
        item.setUpdatedAt(LocalDateTime.now());
        cartItemMapper.updateById(item);
        return toVO(item, product);
    }

    public void delete(Long id) {
        requireOwn(id);
        cartItemMapper.deleteById(id);
    }

    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String userId = AuthContext.userId();
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).in(CartItem::getId, ids));
    }

    public void select(CartSelectRequest request) {
        String userId = AuthContext.userId();
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId);
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            wrapper.in(CartItem::getId, request.getIds());
        }
        List<CartItem> items = cartItemMapper.selectList(wrapper);
        for (CartItem item : items) {
            item.setSelected(Boolean.TRUE.equals(request.getSelected()) ? 1 : 0);
            item.setUpdatedAt(LocalDateTime.now());
            cartItemMapper.updateById(item);
        }
    }

    public List<CartItem> selectedItems(String userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getSelected, 1));
    }

    public void removeByProductIds(String userId, List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).in(CartItem::getProductId, productIds));
    }

    private CartItem requireOwn(Long id) {
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("购物车项不存在");
        }
        return item;
    }

    private CartItemVO toVO(CartItem item, Product product) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(item.getSelected());
        if (product == null) {
            vo.setName("商品已下架");
            vo.setAvailable(false);
            vo.setPrice(BigDecimal.ZERO);
            vo.setSubtotal(BigDecimal.ZERO);
            return vo;
        }
        vo.setName(product.getName());
        vo.setImage(product.getImage());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setStock(product.getStock());
        vo.setAvailable(product.getStatus() != null && product.getStatus() == 1 && product.getStock() > 0);
        vo.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return vo;
    }
}
