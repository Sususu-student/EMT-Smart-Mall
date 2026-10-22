package com.smartmall.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_prompt")
public class AgentPrompt {

    @TableId(type = IdType.INPUT)
    private String promptKey;
    private String name;
    private String description;
    private String content;
    private LocalDateTime updatedAt;
}
