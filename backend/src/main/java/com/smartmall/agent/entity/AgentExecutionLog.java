package com.smartmall.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_execution_log")
public class AgentExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String conversationId;
    private String agentId;
    private String step;
    private String stepName;
    private String input;
    private String output;
    private String status;
    private Integer durationMs;
    private LocalDateTime createdAt;
}
