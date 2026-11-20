package com.bytemall.stockservice.cache;

import com.bytemall.stockservice.entity.StockLog;
import com.bytemall.stockservice.entity.StockMain;
import com.bytemall.stockservice.mapper.StockMainMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StockCacheServiceImpl implements StockCacheService {
    private static final String STOCK_KEY_PREFIX = "stock:sku:";
    private static final String OPERATION_KEY_PREFIX = "stock:op:";
    private static final DefaultRedisScript<Long> APPLY_SCRIPT = script("lua/stock_apply.lua");
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = script("lua/stock_refresh.lua");

    private final StringRedisTemplate redisTemplate;
    private final StockMainMapper stockMainMapper;

    public StockCacheServiceImpl(StringRedisTemplate redisTemplate, StockMainMapper stockMainMapper) {
        this.redisTemplate = redisTemplate;
        this.stockMainMapper = stockMainMapper;
    }

    @Override
    public StockMain findBySkuId(Long skuId) {
        String stockKey = stockKey(skuId);
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(stockKey);
            if (!values.isEmpty()) {
                return fromCache(skuId, values);
            }
        } catch (RuntimeException exception) {
            // 缓存异常或脏数据都不能阻断查询，清理后直接使用数据库结果。
            log.warn("读取SKU库存缓存失败，回源MySQL: {}", skuId, exception);
            deleteQuietly(stockKey);
        }

        StockMain stock = stockMainMapper.selectById(skuId);
        if (stock != null) {
            refreshSafely(stock);
        }
        return stock;
    }

    @Override
    public void syncAfterCommit(StockMain beforeStock, String reservationNo, StockLog.Operation operation,
                                int availableDelta, int reservedDelta) {
        // 必须在注册回调前复制快照，不能保留事务内可能被继续修改的实体引用。
        StockMain snapshot = snapshotOf(beforeStock);
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        if (transactionActive && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    applySafely(snapshot, reservationNo, operation, availableDelta, reservedDelta);
                }
            });
            return;
        }
        if (transactionActive) {
            // 无法注册afterCommit时宁可跳过，也不能让回滚事务提前污染缓存。
            log.warn("当前事务未开启同步，跳过SKU库存缓存写入: {}", snapshot.getSkuId());
            return;
        }
        applySafely(snapshot, reservationNo, operation, availableDelta, reservedDelta);
    }

    private void applySafely(StockMain beforeStock, String reservationNo, StockLog.Operation operation,
                             int availableDelta, int reservedDelta) {
        Long skuId = beforeStock.getSkuId();
        String stockKey = stockKey(skuId);
        String operationKey = OPERATION_KEY_PREFIX + reservationNo + ":" + skuId;
        try {
            long beforeVersion = beforeStock.getVersion();
            Long result = redisTemplate.execute(APPLY_SCRIPT, List.of(stockKey, operationKey),
                    operation.name(), Long.toString(beforeVersion), Long.toString(beforeVersion + 1),
                    Integer.toString(availableDelta), Integer.toString(reservedDelta));
            if (result == null) {
                throw new IllegalStateException("库存缓存Lua未返回执行结果");
            }
            switch (result.intValue()) {
                case 0 -> log.debug("忽略重复或已过期的库存缓存操作: reservationNo={}, skuId={}, operation={}",
                        reservationNo, skuId, operation);
                case 1 -> log.debug("库存缓存已更新: reservationNo={}, skuId={}, operation={}",
                        reservationNo, skuId, operation);
                case 2 -> log.debug("库存缓存不存在，等待读取时回源: skuId={}", skuId);
                case 3 -> log.debug("库存缓存版本不连续，已删除旧key: skuId={}", skuId);
                default -> {
                    log.warn("库存缓存delta或数据校验失败，删除库存key: skuId={}, result={}", skuId, result);
                    deleteQuietly(stockKey);
                }
            }
        } catch (RuntimeException exception) {
            // MySQL已经提交，任何Redis故障都只能降级，不能影响业务调用结果。
            log.warn("同步SKU库存缓存失败，尝试删除库存key: reservationNo={}, skuId={}",
                    reservationNo, skuId, exception);
            deleteQuietly(stockKey);
        }
    }

    private void refreshSafely(StockMain stock) {
        String stockKey = stockKey(stock.getSkuId());
        try {
            Long result = redisTemplate.execute(REFRESH_SCRIPT, List.of(stockKey),
                    String.valueOf(stock.getMerchantId()), String.valueOf(stock.getTotal()),
                    String.valueOf(stock.getAvailable()), String.valueOf(stock.getReserved()),
                    String.valueOf(stock.getVersion()));
            if (result == null) {
                throw new IllegalStateException("库存缓存刷新Lua未返回执行结果");
            }
            if (result < 0) {
                log.warn("MySQL库存数据不满足缓存约束，跳过缓存刷新: skuId={}", stock.getSkuId());
            }
        } catch (RuntimeException exception) {
            log.warn("刷新SKU库存缓存失败，尝试删除库存key: {}", stock.getSkuId(), exception);
            deleteQuietly(stockKey);
        }
    }

    private StockMain fromCache(Long skuId, Map<Object, Object> values) {
        StockMain stock = new StockMain();
        stock.setSkuId(skuId);
        stock.setMerchantId(Long.valueOf(requiredField(values, "merchantId")));
        stock.setTotal(Integer.valueOf(requiredField(values, "total")));
        stock.setAvailable(Integer.valueOf(requiredField(values, "available")));
        stock.setReserved(Integer.valueOf(requiredField(values, "reserved")));
        stock.setVersion(Long.valueOf(requiredField(values, "version")));
        if (stock.getMerchantId() < 0 || stock.getTotal() < 0 || stock.getAvailable() < 0
                || stock.getReserved() < 0 || stock.getVersion() < 0
                || stock.getTotal() != (long) stock.getAvailable() + stock.getReserved()) {
            throw new IllegalArgumentException("库存缓存字段或库存恒等式无效");
        }
        return stock;
    }

    private static String requiredField(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalArgumentException("库存缓存缺少字段: " + field);
        }
        return value.toString();
    }

    private void deleteQuietly(String stockKey) {
        try {
            redisTemplate.delete(stockKey);
        } catch (RuntimeException deleteException) {
            log.warn("删除SKU库存缓存key失败: {}", stockKey, deleteException);
        }
    }

    private static StockMain snapshotOf(StockMain source) {
        StockMain snapshot = new StockMain();
        snapshot.setSkuId(source.getSkuId());
        snapshot.setMerchantId(source.getMerchantId());
        snapshot.setTotal(source.getTotal());
        snapshot.setAvailable(source.getAvailable());
        snapshot.setReserved(source.getReserved());
        snapshot.setVersion(source.getVersion());
        snapshot.setCreatedAt(source.getCreatedAt());
        snapshot.setUpdatedAt(source.getUpdatedAt());
        return snapshot;
    }

    private static String stockKey(Long skuId) {
        return STOCK_KEY_PREFIX + skuId;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
