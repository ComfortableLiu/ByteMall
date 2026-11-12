package com.bytemall.productservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

//告诉它 ProductSpu 这个 Java 实体类对应数据库里的 product_spu 表
@Data
@TableName("product_spu")
public class ProductSpu {
    //    下面的 id 字段是表的主键，由数据库自增生成。
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long categoryId;
    private String title;
    private String subtitle;
    private String brand;
    private String status;
    private Long version;
    private String descriptionHtml;
    private Long salesCount;
    private LocalDateTime publishedAt;
}
