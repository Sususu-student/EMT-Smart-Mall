package com.smartmall.agent.service;

import com.smartmall.common.JsonUtils;
import com.smartmall.product.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FieldValidatorTest {

    private final FieldValidator validator = new FieldValidator();

    private Product product() {
        Product product = new Product();
        product.setId("p_test");
        product.setName("声屿 Air 3 运动版");
        product.setPrice(new BigDecimal("279.00"));
        product.setStock(88);
        product.setAttributes(JsonUtils.toJson(Map.of("续航", 24, "防水等级", "IPX5", "重量", 4.2)));
        return product;
    }

    @Test
    void acceptsGroundedReason() {
        var result = validator.validate(product(), "售价 279 元，IPX5 防水与 24h 续航覆盖跑步场景，4.2g 佩戴负担小。");
        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }

    @Test
    void rejectsPriceMismatch() {
        var result = validator.validate(product(), "售价 299 元，IPX5 防水，续航 24h。");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("价格不一致"));
    }

    @Test
    void rejectsStockMismatch() {
        var result = validator.validate(product(), "售价 279 元，库存 99 件，IPX5 防水。");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("库存不一致"));
    }

    @Test
    void rejectsUngroundedReason() {
        var result = validator.validate(product(), "音质非常出色，外观时尚，值得购买。");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("未引用"));
    }

    @Test
    void rejectsAbsoluteClaim() {
        var result = validator.validate(product(), "售价 279 元，IPX5 防水，100% 不会掉线。");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("绝对化"));
    }
}
