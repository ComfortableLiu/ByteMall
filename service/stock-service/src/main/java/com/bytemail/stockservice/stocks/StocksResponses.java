package com.bytemail.stockservice.stocks;

public class StocksResponses {
    /**
     * 库存信息
     * @param skuId
     * @param total 总数
     * @param availableStock 可用库存
     * @param reservedStock 预售库存
     */
    public record Sku(String skuId, int total, int availableStock, int reservedStock) {
    }
}
