package com.smartmall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.INPUT)
    private String id;
    private String parentId;
    private String name;
    private String icon;
    private String description;
    private String attributeSchema;
    private Integer sortOrder;
    private Integer status;
}
