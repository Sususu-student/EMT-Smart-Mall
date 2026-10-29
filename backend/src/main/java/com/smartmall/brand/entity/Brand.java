package com.smartmall.brand.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("brand")
public class Brand {

    private String id;
    private String name;
    /** 品牌主张，一句话 */
    private String slogan;
    /** 产地/发源地 */
    private String origin;
    /** 创立年份 */
    private String founded;
    /** 定位，例如「运动音频」 */
    private String positioning;
    /** 简介，2-3 句 */
    private String intro;
    /** 品牌故事，更长的叙述 */
    private String story;
    /** 核心卖点，用于标签展示 */
    private String highlight;
    /** 品牌首字，用于占位标识 */
    private String logoText;
    /** 主题色，用于弹层点缀 */
    private String accent;
    private Integer sortOrder;
    private Integer status;
}