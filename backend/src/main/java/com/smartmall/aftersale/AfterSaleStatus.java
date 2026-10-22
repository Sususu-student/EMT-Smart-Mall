package com.smartmall.aftersale;

public final class AfterSaleStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private AfterSaleStatus() {
    }

    public static String label(String status) {
        return switch (status == null ? "" : status) {
            case PENDING -> "待处理";
            case APPROVED -> "已同意";
            case REJECTED -> "已拒绝";
            default -> status;
        };
    }

    public static String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "REFUND" -> "仅退款";
            case "RETURN" -> "退货退款";
            case "EXCHANGE" -> "换货";
            default -> type;
        };
    }
}
