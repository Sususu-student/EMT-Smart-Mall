package com.smartmall.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("compare_record")
public class CompareRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String productIds;
    private String result;
    private LocalDateTime createdAt;
}
