
package com.bytemall.productservice.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;
public record CreateProductRequest(
        /*
         * 商品主标题
         */
        @NotBlank String title,
        /*
          商品副标题 可省略。
         */
        String subtitle,
        /*
         *商品所属分类的 ID
         */
        @NotBlank String categoryId,
        /*
         * 	品牌名称,可省略。
         */
        String brand,
        /*
         * 	商品详情的 HTML,可省略，保存前需要净化。
         */
        String descriptionHtml,
        /*
        * 	商品图片列表。可省略。
        */
        List<@NotNull @Valid ImageRequest> images,
        /*
        * 	商品的可售规格列表
        */
        @NotEmpty List<@NotNull @Valid SkuRequest> skus
) {

    public record SkuRequest(
            /*
             *	这一款 SKU 的业务编码，
             */
            @NotBlank String skuCode,
            /*
             *		这一款的规格组合，
             */
            Map<String, String> specs,
            /*
             *		当前售价，单位是分
             */
            @NotNull @PositiveOrZero Long price,
            /*
             *		展示用的原价，例如 24900 表示 249 元
             */
            @PositiveOrZero Long originalPrice,
            /*
             *		是否启用这一款 SKU
             */
            Boolean enabled
    ) { }

    public record ImageRequest(
            /*
             *	图片地址
             */
            @NotBlank String url,
            /*
             *	图片替代文字
             */
            String altText,
            /*
             *	图片展示顺序
             */
            Integer sortOrder,
            /*
             *	是否为商品主图
             */
            Boolean isPrimary
    ) { }
}