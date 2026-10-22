package com.smartmall.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminConversationVO {

    private String id;
    private String userId;
    private String username;
    private String nickname;
    private String title;
    private int turnCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
