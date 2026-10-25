package com.smartmall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String subtitle;
    private String categoryId;
    private String brand;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sales;
    private BigDecimal rating;
    private Integer reviewCount;
    private String description;
    private String attributes;
    private String tags;
    private String image;
    /** 远程图加载失败时的本地兜底图 */
    private String imageFallback;
    /** 发布该商品的卖家用户 ID；平台自营为空 */
    private String sellerId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
