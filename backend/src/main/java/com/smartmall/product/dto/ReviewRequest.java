package com.smartmall.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequest {

    @Min(value = 1, message = "评分最低为 1")
    @Max(value = 5, message = "评分最高为 5")
    private Integer rating = 5;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容最多 500 字")
    private String content;
}
