package com.smartmall.order;

public final class OrderStatus {

    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CLOSED = "CLOSED";

    private OrderStatus() {
    }

    public static String label(String status) {
        return switch (status == null ? "" : status) {
            case PENDING_PAYMENT -> "待支付";
            case PAID -> "已支付";
            case SHIPPED -> "已发货";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case CLOSED -> "已关闭";
            default -> status;
        };
    }
}
