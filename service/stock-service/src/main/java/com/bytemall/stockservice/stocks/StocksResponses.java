package com.bytemall.stockservice.stocks;

import com.bytemall.stockservice.entity.StockMain;

public class StocksResponses {
    /**
     * 库存信息
     *
     * @param skuId
     * @param total          总数
     * @param availableStock 可用库存
     * @param reservedStock  预售库存
     * @param version        版本号
     */
    public record Sku(Long skuId, int total, int availableStock, int reservedStock, long version) {
        public static Sku of(StockMain stockMain) {
            if (stockMain == null) return null;
            return new Sku(stockMain.getSkuId(), stockMain.getTotal(), stockMain.getAvailable(), stockMain.getReserved(), stockMain.getVersion());
        }
    }
}
