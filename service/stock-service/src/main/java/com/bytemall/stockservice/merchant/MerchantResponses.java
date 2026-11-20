package com.bytemall.stockservice.merchant;

import java.util.Map;

public class MerchantResponses {

    /**
     * 商家库存及商品摘要；商品记录缺失时摘要字段为空，不填充演示数据。
     */
    public record MerchantStockItem(
            String skuId,
            int total,
            int availableStock,
            int reservedStock,
            long version,
            String spuId,
            String productTitle,
            String productImage,
            String skuCode,
            Map<String, String> specs
    ) {
    }
}
