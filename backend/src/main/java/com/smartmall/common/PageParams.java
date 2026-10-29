package com.smartmall.common;

/**
 * 分页参数统一校验。
 *
 * 之前各 Service 用 Math.max/Math.min 静默兜底，导致：
 * - page=0 / page=-1 被悄悄改成第 1 页，调用方以为生效了
 * - size=200 被悄悄截断成 60，上限无人知晓
 * 现在改为非法值直接抛 400，并在错误信息里说明合法范围。
 */
public final class PageParams {

    /** 通用默认每页条数 */
    public static final long DEFAULT_SIZE = 12;
    /** 通用最大每页条数 */
    public static final long MAX_SIZE = 60;

    private PageParams() {
    }

    /** 校验页码与每页条数，返回规范后的 {page, size} */
    public static long[] check(long page, long size) {
        return check(page, size, MAX_SIZE);
    }

    /** 按接口自身允许的上限校验 */
    public static long[] check(long page, long size, long maxSize) {
        if (page < 1) {
            throw new BizException("页码必须大于等于 1，当前为 " + page);
        }
        if (size < 1) {
            throw new BizException("每页条数必须大于等于 1，当前为 " + size);
        }
        if (size > maxSize) {
            throw new BizException("每页条数不能超过 " + maxSize + "，当前为 " + size);
        }
        return new long[]{page, size};
    }
}