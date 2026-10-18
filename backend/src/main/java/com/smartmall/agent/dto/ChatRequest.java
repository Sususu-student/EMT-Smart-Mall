package com.smartmall.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "单条消息最多 500 字")
    private String message;

    private String conversationId;
}
