package com.smartmall.brand.dto;

import lombok.Data;

/**
 * 品牌档案 VO：首页品牌条与品牌弹层使用。
 */
@Data
public class BrandVO {

    private String id;
    private String name;
    private String slogan;
    private String origin;
    private String founded;
    private String positioning;
    private String intro;
    private String story;
    private String highlight;
    private String logoText;
    private String accent;
    /** 该品牌在售商品数 */
    private long productCount;
}