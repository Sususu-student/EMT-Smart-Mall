package com.smartmall.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewAdminVO {

    private Long id;
    private String productId;
    private String productName;
    private String userId;
    private String username;
    private Integer rating;
    private String content;
    private Integer status;
    private LocalDateTime createdAt;
}
