package com.smartmall.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String title;
    private String slots;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
