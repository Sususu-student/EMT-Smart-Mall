package com.smartmall.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String link;
    private Integer readStatus;
    private LocalDateTime createdAt;
}
