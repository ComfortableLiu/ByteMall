package com.bytemall.productservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;
    private String skuCode;
    private String specJson;
    private byte[] specHash;
    private Long price;
    private Long originalPrice;
    private Boolean enabled;
}