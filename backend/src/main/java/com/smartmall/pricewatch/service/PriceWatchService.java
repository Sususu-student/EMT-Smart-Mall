package com.smartmall.pricewatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmall.auth.AuthContext;
import com.smartmall.notification.service.NotificationService;
import com.smartmall.pricewatch.entity.PriceWatch;
import com.smartmall.pricewatch.mapper.PriceWatchMapper;
import com.smartmall.product.entity.Product;
import com.smartmall.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 降价提醒：用户对商品设置提醒，系统每天扫描价格变动并发送站内信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceWatchService {

    private final PriceWatchMapper watchMapper;
    private final ProductMapper productMapper;
    private final NotificationService notificationService;

    /** 降价幅度低于 1% 不通知，避免一分钱的噪音打扰 */
    private static final BigDecimal MIN_DROP_RATE = new BigDecimal("0.01");

    /** 加入/取消降价提醒，返回加入后的状态 */
    public boolean toggle(String productId) {
        String userId = AuthContext.userId();
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new com.smartmall.common.BizException("商品不存在");
        }
        PriceWatch existing = watchMapper.selectOne(new LambdaQueryWrapper<PriceWatch>()
                .eq(PriceWatch::getUserId, userId)
                .eq(PriceWatch::getProductId, productId));
        if (existing != null) {
            watchMapper.deleteById(existing.getId());
            return false;
        }
        PriceWatch watch = new PriceWatch();
        watch.setUserId(userId);
        watch.setProductId(productId);
        watch.setBaselinePrice(product.getPrice());
        watch.setNotified(0);
        watch.setCreatedAt(LocalDateTime.now());
        watchMapper.insert(watch);
        return true;
    }

    /** 查询当前用户是否已对该商品设置提醒 */
    public boolean watching(String productId) {
        String userId = AuthContext.userId();
        return watchMapper.selectCount(new LambdaQueryWrapper<PriceWatch>()
                .eq(PriceWatch::getUserId, userId)
                .eq(PriceWatch::getProductId, productId)) > 0;
    }

    /** 我关注且尚未通知的商品 id 集合，商品列表用来显示"已关注"角标 */
    public Set<String> myWatchedIds() {
        String userId = AuthContext.userId();
        return watchMapper.selectList(new LambdaQueryWrapper<PriceWatch>()
                        .eq(PriceWatch::getUserId, userId))
                .stream().map(PriceWatch::getProductId).collect(Collectors.toSet());
    }

    /**
     * 每天 09:00 扫描降价。只处理尚未通知过的记录，
     * 通知后标记 notified，避免同一商品反复打扰。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scanPriceDrops() {
        List<PriceWatch> pending = watchMapper.selectList(new LambdaQueryWrapper<PriceWatch>()
                .eq(PriceWatch::getNotified, 0)
                .last("LIMIT 500"));
        if (pending.isEmpty()) {
            return;
        }
        Set<String> productIds = pending.stream().map(PriceWatch::getProductId).collect(Collectors.toSet());
        var productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        int sent = 0;
        for (PriceWatch watch : pending) {
            Product product = productMap.get(watch.getProductId());
            if (product == null || product.getPrice() == null || watch.getBaselinePrice() == null) {
                continue;
            }
            BigDecimal baseline = watch.getBaselinePrice();
            BigDecimal current = product.getPrice();
            if (current.compareTo(baseline) >= 0) {
                continue;
            }
            // 目标价设置了的话，必须低于目标价才提醒
            if (watch.getTargetPrice() != null && current.compareTo(watch.getTargetPrice()) > 0) {
                continue;
            }
            // 降价幅度需超过 1%
            BigDecimal dropRate = baseline.subtract(current)
                    .divide(baseline, 4, RoundingMode.HALF_UP);
            if (dropRate.compareTo(MIN_DROP_RATE) < 0) {
                continue;
            }
            BigDecimal drop = baseline.subtract(current);
            notificationService.notify(
                    watch.getUserId(),
                    "PRICE_DROP",
                    "你关注的商品降价了",
                    String.format("「%s」由 ¥%s 降至 ¥%s，降了 ¥%s，去看看？",
                            product.getName(),
                            baseline.setScale(2, RoundingMode.HALF_UP),
                            current.setScale(2, RoundingMode.HALF_UP),
                            drop.setScale(2, RoundingMode.HALF_UP)),
                    "/product/" + product.getId());
            watch.setNotified(1);
            watch.setNotifiedAt(LocalDateTime.now());
            watchMapper.updateById(watch);
            sent++;
        }
        if (sent > 0) {
            log.info("降价提醒扫描完成，发出 {} 条通知", sent);
        }
    }
}