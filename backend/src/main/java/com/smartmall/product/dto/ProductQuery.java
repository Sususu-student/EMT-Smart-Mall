package com.smartmall.product.dto;

import com.smartmall.common.BizException;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductQuery {

    /** 单页最大条数：超出直接报错，而不是静默截断 */
    public static final long MAX_SIZE = 60;
    /** 默认每页条数 */
    public static final long DEFAULT_SIZE = 12;

    private long page = 1;
    private long size = DEFAULT_SIZE;
    private String categoryId;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String brand;
    private String sort = "default";

    /**
     * 校验分页参数。
     * 非法值直接抛 400，避免"参数传错却拿到第一页"这类静默兜底难以排查。
     */
    public void normalize() {
        if (page < 1) {
            throw new BizException("页码必须大于等于 1，当前为 " + page);
        }
        if (size < 1) {
            throw new BizException("每页条数必须大于等于 1，当前为 " + size);
        }
        if (size > MAX_SIZE) {
            throw new BizException("每页条数不能超过 " + MAX_SIZE + "，当前为 " + size);
        }
    }
}
