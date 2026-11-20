package com.bytemall.stockservice.cache;

import com.bytemall.stockservice.entity.StockLog;
import com.bytemall.stockservice.entity.StockMain;

public interface StockCacheService {

    StockMain findBySkuId(Long skuId);

    void syncAfterCommit(StockMain beforeStock, String reservationNo, StockLog.Operation operation,
                         int availableDelta, int reservedDelta);
}
