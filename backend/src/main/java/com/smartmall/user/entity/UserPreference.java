package com.smartmall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String preferenceKey;
    private String preferenceValue;
    private BigDecimal weight;
    private LocalDateTime updatedAt;
}
