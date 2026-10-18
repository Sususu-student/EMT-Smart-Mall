package com.smartmall.user.dto;

import lombok.Data;

@Data
public class ProfileVO {

    private UserVO user;
    private long orderCount;
    private long favoriteCount;
    private long cartCount;
}
