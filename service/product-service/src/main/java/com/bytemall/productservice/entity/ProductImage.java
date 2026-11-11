package com.bytemall.productservice.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getPrimaryImage() {
        return primaryImage;
    }

    public void setPrimaryImage(Boolean primaryImage) {
        this.primaryImage = primaryImage;
    }
}
