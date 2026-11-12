package com.bytemall.productservice.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("category")
public class Category {

    @TableId
    private Long id;

    private Boolean enabled;

    private Long parentId;

    private String name;

    private Integer level;

    private Integer sortOrder;
}