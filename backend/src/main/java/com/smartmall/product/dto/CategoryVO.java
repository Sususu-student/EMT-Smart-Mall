package com.smartmall.product.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {

    private String id;
    private String parentId;
    private String name;
    private String icon;
    private String description;
    private Integer sortOrder;
    private List<CategoryVO> children = new ArrayList<>();
}
