package com.smartmall.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最多 50 字")
    private String name;

    private String parentId;

    @Size(max = 20, message = "图标最多 20 字")
    private String icon;

    @Size(max = 200, message = "描述最多 200 字")
    private String description;

    private Integer sortOrder = 10;

    private Integer status = 1;
}
