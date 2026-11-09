package com.bytemail.stockservice.stocks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StocksService {

    public StocksResponses.Sku getStockBySkuId(String skuId) {
        // TODO 查库存
        return new StocksResponses.Sku(skuId, 100, 100, 0);
    }
}
