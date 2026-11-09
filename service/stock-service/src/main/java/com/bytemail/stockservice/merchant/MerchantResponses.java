package com.bytemail.stockservice.merchant;

import java.util.Map;

public class MerchantResponses {

    /**
     * 库存信息
     * @param skuId skuId
     * @param total 总库存
     * @param availableStock 可用库存
     * @param reservedStock 预售库存
     * @param spuId spuId
     * @param productTitle 产品标题
     * @param productImage 产品图片
     * @param skuCode skuCode
     * @param specs 规格
     */
    public record Stock(
            String skuId,
            int total,
            int availableStock,
            int reservedStock,
            String spuId,
            String productTitle,
            String productImage,
            String skuCode,
            Map<String, String> specs
    ) {
    }
}
