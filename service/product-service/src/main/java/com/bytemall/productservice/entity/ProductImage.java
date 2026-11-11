package com.bytemall.productservice.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_image")
public class ProductImage {
    @TableId(type= IdType.AUTO)
    private Long id;
    private Long spuId;
    private String url;
    private String altText;
    private Integer sortOrder;
    /*
    * isPrimary 表示这张图片是不是商品主图。
    */
    @TableField("is_primary")
    private Boolean primaryImage;
}
