package com.smartmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItem {

    /** 子项发货状态：待发货 */
    public static final String SHIP_PENDING = "PENDING";
    /** 子项发货状态：已发货 */
    public static final String SHIP_SHIPPED = "SHIPPED";
    /** 平台自营商品没有归属卖家，用该标识占位 */
    public static final String PLATFORM_SELLER = "__platform__";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderId;
    private String productId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer quantity;

    /** 发货归属卖家，下单时按商品固化，支持多卖家订单各自发货 */
    private String sellerId;
    private String shipStatus;
    private String carrier;
    private String trackingNo;
    private LocalDateTime shippedAt;
    private String refundStatus;
}
