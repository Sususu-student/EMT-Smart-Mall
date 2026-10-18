package com.smartmall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_address")
public class UserAddress {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Integer isDefault;
    private LocalDateTime createdAt;

    public String fullAddress() {
        return String.join("", nullSafe(province), nullSafe(city), nullSafe(district), nullSafe(detail));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
