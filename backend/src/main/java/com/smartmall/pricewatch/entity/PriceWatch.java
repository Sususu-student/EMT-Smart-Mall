package com.smartmall.pricewatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 降价提醒：用户对某商品设置的关注记录 */
@Data
@TableName("price_watch")
public class PriceWatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String productId;
    /** 加入提醒时的价格，用于判断是否真的降价 */
    private BigDecimal baselinePrice;
    /** 目标价，为空表示任意降价都通知 */
    private BigDecimal targetPrice;
    private Integer notified;
    private LocalDateTime notifiedAt;
    private LocalDateTime createdAt;
}
