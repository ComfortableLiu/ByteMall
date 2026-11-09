package com.bytemail.stockservice.stocks;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.stockservice.entity.StockMain;
import com.bytemail.stockservice.mapper.StockMainMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StocksService {

    private final StockMainMapper stockMainMapper;

    public StocksResponses.Sku getStockBySkuId(Long skuId) {
        log.info("Getting stock by sku id {}", skuId);
        StockMain stockMain = stockMainMapper.selectBySkuIdForUpdate(skuId);
        return StocksResponses.Sku.of(stockMain);
    }
}
