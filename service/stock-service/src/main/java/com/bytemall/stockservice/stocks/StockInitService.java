package com.bytemall.stockservice.stocks;

import com.bytemall.stock.proto.InitStockItem;
import com.bytemall.stock.proto.InitStocksRequest;
import com.bytemall.stock.proto.InitStocksResponse;
import com.bytemall.stockservice.mapper.StockMainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 库存行初始化服务：商品创建后为新SKU补齐0库存记录。
 */
@Service
@RequiredArgsConstructor
public class StockInitService {

    private final StockMainMapper stockMainMapper;

    /**
     * 缺失的SKU批量创建0库存行，已存在的跳过；INSERT IGNORE保证幂等可重放。
     */
    public InitStocksResponse initStocks(InitStocksRequest request) {
        if (request.getItemsCount() == 0 || request.getItemsCount() > 100) {
            throw new IllegalArgumentException("items必须包含1到100项");
        }

        InitStocksResponse.Builder response = InitStocksResponse.newBuilder();
        for (InitStockItem item : request.getItemsList()) {
            if (item.getSkuId() <= 0 || item.getMerchantId() <= 0) {
                throw new IllegalArgumentException("skuId和merchantId必须为正数");
            }
            if (stockMainMapper.insertInit(item.getSkuId(), item.getMerchantId()) == 1) {
                response.addCreatedSkuIds(String.valueOf(item.getSkuId()));
            } else {
                response.addExistingSkuIds(String.valueOf(item.getSkuId()));
            }
        }
        return response.build();
    }
}
