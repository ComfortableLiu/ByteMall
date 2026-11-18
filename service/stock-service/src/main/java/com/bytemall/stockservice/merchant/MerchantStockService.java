package com.bytemall.stockservice.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.stockservice.cache.StockCacheService;
import com.bytemall.stockservice.entity.StockLog;
import com.bytemall.stockservice.entity.StockMain;
import com.bytemall.stockservice.mapper.StockLogMapper;
import com.bytemall.stockservice.mapper.StockMainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantStockService {
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final StockMainMapper stockMainMapper;
    private final StockLogMapper stockLogMapper;
    private final StockCacheService stockCacheService;

    @Transactional(readOnly = true)
    public List<StockMain> listByMerchant(Long merchantId, boolean lowStock) {
        return stockMainMapper.selectList(Wrappers.<StockMain>lambdaQuery()
                .eq(StockMain::getMerchantId, merchantId)
                .le(lowStock, StockMain::getAvailable, LOW_STOCK_THRESHOLD)
                .orderByAsc(StockMain::getSkuId));
    }

    /**
     * 外部RPC均在此事务之外完成，行锁只覆盖库存更新、流水和缓存提交后同步注册。
     */
    @Transactional
    public StockMain adjust(
            Long skuId,
            Long merchantId,
            Long userId,
            int availableDelta,
            String reason,
            Long expectedVersion,
            String requestId
    ) {
        StockMain beforeStock = stockMainMapper.selectBySkuIdForUpdate(skuId);
        if (beforeStock == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU库存不存在: " + skuId);
        }
        if (!Objects.equals(beforeStock.getMerchantId(), merchantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权调整其他商家的SKU库存");
        }
        if (!Objects.equals(beforeStock.getVersion(), expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "库存版本已变化，请刷新后重试");
        }
        requireValidStock(beforeStock);

        long afterAvailable = (long) beforeStock.getAvailable() + availableDelta;
        long afterTotal = (long) beforeStock.getTotal() + availableDelta;
        if (afterAvailable < 0 || afterTotal < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调整后的可售库存或总库存不能小于0");
        }
        if (afterAvailable > Integer.MAX_VALUE || afterTotal > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调整后的库存超出支持范围");
        }

        int updated = stockMainMapper.adjustAvailableStock(
                skuId, merchantId, availableDelta, expectedVersion);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "库存状态已变化，请刷新后重试");
        }

        String adjustmentNo = "ADJUST-" + UUID.randomUUID();
        StockLog stockLog = new StockLog();
        stockLog.setSkuId(skuId);
        stockLog.setReservationNo(adjustmentNo);
        stockLog.setOperation(StockLog.Operation.ADJUST);
        stockLog.setAvailableDelta(availableDelta);
        stockLog.setReservedDelta(0);
        stockLog.setBeforeAvailable(beforeStock.getAvailable());
        stockLog.setAfterAvailable((int) afterAvailable);
        stockLog.setReason(reason);
        stockLog.setOperatorId(userId);
        stockLog.setRequestId(requestId);
        try {
            if (stockLogMapper.insertLog(stockLog) != 1) {
                throw new IllegalStateException("库存调整流水写入失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "库存调整流水冲突，请重试", exception);
        }

        StockMain afterStock = copyWithAdjustment(
                beforeStock, (int) afterAvailable, (int) afterTotal);
        // 缓存实现负责注册事务提交后的同步，这里必须保持在事务边界内。
        stockCacheService.syncAfterCommit(
                beforeStock, adjustmentNo, StockLog.Operation.ADJUST, availableDelta, 0);
        return afterStock;
    }

    private static void requireValidStock(StockMain stock) {
        boolean invalid = stock.getTotal() == null
                || stock.getAvailable() == null
                || stock.getReserved() == null
                || stock.getVersion() == null
                || stock.getAvailable() < 0
                || stock.getReserved() < 0
                || stock.getTotal() != (long) stock.getAvailable() + stock.getReserved()
                || stock.getVersion() < 0
                || stock.getVersion() == Long.MAX_VALUE;
        if (invalid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU库存数据状态异常，无法调整");
        }
    }

    private static StockMain copyWithAdjustment(StockMain before, int available, int total) {
        StockMain after = new StockMain();
        after.setSkuId(before.getSkuId());
        after.setMerchantId(before.getMerchantId());
        after.setAvailable(available);
        after.setReserved(before.getReserved());
        after.setTotal(total);
        after.setVersion(before.getVersion() + 1);
        after.setCreatedAt(before.getCreatedAt());
        return after;
    }
}
