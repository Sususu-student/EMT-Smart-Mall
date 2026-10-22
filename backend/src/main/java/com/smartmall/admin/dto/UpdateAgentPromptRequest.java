package com.smartmall.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAgentPromptRequest {

    @NotBlank(message = "Prompt 内容不能为空")
    @Size(max = 6000, message = "Prompt 内容最多 6000 字")
    private String content;
}
