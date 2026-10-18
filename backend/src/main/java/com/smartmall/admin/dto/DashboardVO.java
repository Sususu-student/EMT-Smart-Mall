package com.smartmall.admin.dto;

import com.smartmall.order.dto.OrderVO;
import com.smartmall.product.dto.ProductVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardVO {

    private long userCount;
    private long productCount;
    private long orderCount;
    private long todayOrderCount;
    private long agentLogCount;
    private BigDecimal totalSales = BigDecimal.ZERO;
    private BigDecimal todaySales = BigDecimal.ZERO;
    private List<StatusCount> orderStatus = new ArrayList<>();
    private List<ProductVO> topProducts = new ArrayList<>();
    private List<OrderVO> recentOrders = new ArrayList<>();
    private List<DailyPoint> trend = new ArrayList<>();

    @Data
    public static class StatusCount {
        private String status;
        private String label;
        private long count;

        public StatusCount(String status, String label, long count) {
            this.status = status;
            this.label = label;
            this.count = count;
        }
    }

    @Data
    public static class DailyPoint {
        private String date;
        private long orders;
        private BigDecimal amount;

        public DailyPoint(String date, long orders, BigDecimal amount) {
            this.date = date;
            this.orders = orders;
            this.amount = amount;
        }
    }
}
