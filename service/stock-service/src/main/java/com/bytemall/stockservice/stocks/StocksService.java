package com.bytemall.stockservice.stocks;

import com.bytemall.stockservice.cache.StockCacheService;
import com.bytemall.stockservice.entity.StockMain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class StocksService {

    private final StockCacheService stockCacheService;

    public StocksResponses.Sku getStockBySkuId(Long skuId) {
        log.info("Getting stock by sku id {}", skuId);
        StockMain stockMain = stockCacheService.findBySkuId(skuId);
        if (stockMain == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU库存不存在: " + skuId);
        }
        return StocksResponses.Sku.of(stockMain);
    }
}
