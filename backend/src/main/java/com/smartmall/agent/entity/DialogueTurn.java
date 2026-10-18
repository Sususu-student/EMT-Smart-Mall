package com.smartmall.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dialogue_turn")
public class DialogueTurn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String role;
    private String content;
    private String intent;
    private String slots;
    private String products;
    private String taskId;
    private LocalDateTime createdAt;
}
