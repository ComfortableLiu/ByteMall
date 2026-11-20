package com.bytemall.stockservice.stocks;

import com.bytemall.stockservice.entity.StockMain;

public class StocksResponses {
    /**
     * 公开库存视图，仅暴露可售量与版本，不透出总量和预扣量。
     *
     * @param skuId          SKU ID
     * @param availableStock 可用库存
     * @param version        版本号
     */
    public record Sku(String skuId, int availableStock, long version) {
        public static Sku of(StockMain stockMain) {
            if (stockMain == null) return null;
            return new Sku(stockMain.getSkuId().toString(), stockMain.getAvailable(), stockMain.getVersion());
        }
    }
}
