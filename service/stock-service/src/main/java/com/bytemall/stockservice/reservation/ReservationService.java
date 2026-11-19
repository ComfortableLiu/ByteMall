package com.bytemall.stockservice.reservation;

import com.bytemall.stock.proto.ConfirmReservationResponse;
import com.bytemall.stock.proto.ReleaseReservationResponse;
import com.bytemall.stock.proto.ReservationItem;
import com.bytemall.stock.proto.ReserveStockRequest;
import com.bytemall.stock.proto.ReserveStockResponse;
import com.bytemall.stockservice.cache.StockCacheService;
import com.bytemall.stockservice.entity.StockLog;
import com.bytemall.stockservice.entity.StockMain;
import com.bytemall.stockservice.entity.StockReservation;
import com.bytemall.stockservice.entity.StockReservationItem;
import com.bytemall.stockservice.mapper.StockLogMapper;
import com.bytemall.stockservice.mapper.StockMainMapper;
import com.bytemall.stockservice.mapper.StockReservationItemMapper;
import com.bytemall.stockservice.mapper.StockReservationMapper;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReservationService {
    // stock_reservation/stock_log 的 reservation_no 均为 varchar(32)，校验上限与列宽一致。
    private static final int MAX_RESERVATION_NO_LENGTH = 32;
    private static final int MAX_ORDER_NO_LENGTH = 64;
    private static final int MAX_REASON_LENGTH = 255;

    private final StockReservationMapper reservationMapper;
    private final StockReservationItemMapper reservationItemMapper;
    private final StockMainMapper stockMainMapper;
    private final StockLogMapper stockLogMapper;
    private final StockCacheService stockCacheService;

    /**
     * 整个预扣过程只使用一个数据库事务，缓存操作通过afterCommit在提交后执行。
     */
    @Transactional
    public ReserveStockResponse reserve(ReserveStockRequest request) {
        NormalizedRequest normalized = normalize(request);
        StockReservation draft = new StockReservation();
        draft.setReservationNo(normalized.reservationNo());
        draft.setOrderNo(normalized.orderNo());
        draft.setRequestHash(normalized.requestHash());
        draft.setExpireAt(normalized.expireAt());

        int inserted = reservationMapper.insertIgnore(draft);
        StockReservation reservation = reservationMapper
                .selectByReservationNoForUpdate(normalized.reservationNo());
        if (reservation == null) {
            throw new ReservationException(Status.Code.ABORTED, "预扣单创建未完成，请重试");
        }
        List<StockReservationItem> items;
        if (inserted == 0) {
            items = loadItems(reservation.getId());
            verifyRepeatedRequest(normalized, reservation, items);
            return reserveResponse(reservation, items);
        }

        items = normalized.items();
        for (StockReservationItem item : items) {
            item.setReservationId(reservation.getId());
            reservationItemMapper.insertItem(item);
        }
        for (StockReservationItem item : items) {
            applyReserve(reservation, item);
        }
        return reserveResponse(reservation, items);
    }

    @Transactional
    public ConfirmReservationResponse confirm(String reservationNo) {
        String normalizedNo = requiredText(reservationNo, "reservationNo", MAX_RESERVATION_NO_LENGTH);
        StockReservation reservation = lockReservation(normalizedNo);
        List<StockReservationItem> items = loadItems(reservation.getId());

        if (reservation.getStatus() == StockReservation.Status.CONFIRMED) {
            return confirmResponse(reservation, items);
        }
        requireReserved(reservation, "确认");

        for (StockReservationItem item : items) {
            applyConfirm(reservation, item);
        }
        if (reservationMapper.confirmReserved(reservation.getId()) != 1) {
            throw new ReservationException(Status.Code.ABORTED, "预扣单状态已变化，请重试确认");
        }
        return ConfirmReservationResponse.newBuilder()
                .setReservationNo(reservation.getReservationNo())
                .setOrderNo(reservation.getOrderNo())
                .setStatus(StockReservation.Status.CONFIRMED.name())
                .addAllItems(toProtoItems(items))
                .build();
    }

    /**
     * 主动取消和超时任务共用此入口，每次调用独立提交或回滚。
     */
    @Transactional
    public ReleaseReservationResponse release(String reservationNo, String reason) {
        String normalizedNo = requiredText(reservationNo, "reservationNo", MAX_RESERVATION_NO_LENGTH);
        String normalizedReason = requiredText(reason, "reason", MAX_REASON_LENGTH);
        StockReservation reservation = lockReservation(normalizedNo);
        List<StockReservationItem> items = loadItems(reservation.getId());

        if (reservation.getStatus() == StockReservation.Status.RELEASED) {
            return releaseResponse(reservation, items);
        }
        if (reservation.getStatus() == StockReservation.Status.CONFIRMED) {
            throw new ReservationException(Status.Code.FAILED_PRECONDITION, "已确认的预扣单不能释放");
        }
        requireReserved(reservation, "释放");

        for (StockReservationItem item : items) {
            applyRelease(reservation, item);
        }
        if (reservationMapper.releaseReserved(reservation.getId(), normalizedReason) != 1) {
            throw new ReservationException(Status.Code.ABORTED, "预扣单状态已变化，请重试释放");
        }
        return ReleaseReservationResponse.newBuilder()
                .setReservationNo(reservation.getReservationNo())
                .setOrderNo(reservation.getOrderNo())
                .setStatus(StockReservation.Status.RELEASED.name())
                .addAllItems(toProtoItems(items))
                .build();
    }

    private void applyReserve(StockReservation reservation, StockReservationItem item) {
        StockMain stock = lockStock(item.getSkuId());
        requireStockInvariant(stock);
        int quantity = item.getQuantity();
        if (stock.getAvailable() < quantity) {
            throw new ReservationException(Status.Code.RESOURCE_EXHAUSTED,
                    "SKU可售库存不足: " + item.getSkuId());
        }
        if (stockMainMapper.reserveStock(stock.getSkuId(), quantity, stock.getVersion()) != 1) {
            throw new ReservationException(Status.Code.ABORTED, "库存预扣冲突，请重试: " + item.getSkuId());
        }
        insertLog(reservation, item, StockLog.Operation.RESERVE,
                -quantity, quantity, stock.getAvailable(), stock.getAvailable() - quantity);
        stockCacheService.syncAfterCommit(stock, reservation.getReservationNo(), StockLog.Operation.RESERVE,
                -quantity, quantity);
    }

    private void applyConfirm(StockReservation reservation, StockReservationItem item) {
        StockMain stock = lockStock(item.getSkuId());
        requireStockInvariant(stock);
        int quantity = item.getQuantity();
        if (stock.getReserved() < quantity || stock.getTotal() < quantity) {
            throw new ReservationException(Status.Code.FAILED_PRECONDITION,
                    "SKU预扣库存与预扣单不一致: " + item.getSkuId());
        }
        if (stockMainMapper.confirmReservation(stock.getSkuId(), quantity, stock.getVersion()) != 1) {
            throw new ReservationException(Status.Code.ABORTED, "库存确认冲突，请重试: " + item.getSkuId());
        }
        insertLog(reservation, item, StockLog.Operation.CONFIRM,
                0, -quantity, stock.getAvailable(), stock.getAvailable());
        stockCacheService.syncAfterCommit(stock, reservation.getReservationNo(), StockLog.Operation.CONFIRM,
                0, -quantity);
    }

    private void applyRelease(StockReservation reservation, StockReservationItem item) {
        StockMain stock = lockStock(item.getSkuId());
        requireStockInvariant(stock);
        int quantity = item.getQuantity();
        if (stock.getReserved() < quantity) {
            throw new ReservationException(Status.Code.FAILED_PRECONDITION,
                    "SKU预扣库存与预扣单不一致: " + item.getSkuId());
        }
        if (stockMainMapper.releaseReservation(stock.getSkuId(), quantity, stock.getVersion()) != 1) {
            throw new ReservationException(Status.Code.ABORTED, "库存释放冲突，请重试: " + item.getSkuId());
        }
        insertLog(reservation, item, StockLog.Operation.RELEASE,
                quantity, -quantity, stock.getAvailable(), stock.getAvailable() + quantity);
        stockCacheService.syncAfterCommit(stock, reservation.getReservationNo(), StockLog.Operation.RELEASE,
                quantity, -quantity);
    }

    private StockReservation lockReservation(String reservationNo) {
        StockReservation reservation = reservationMapper.selectByReservationNoForUpdate(reservationNo);
        if (reservation == null) {
            throw new ReservationException(Status.Code.NOT_FOUND, "预扣单不存在: " + reservationNo);
        }
        return reservation;
    }

    private StockMain lockStock(Long skuId) {
        StockMain stock = stockMainMapper.selectBySkuIdForUpdate(skuId);
        if (stock == null) {
            throw new ReservationException(Status.Code.NOT_FOUND, "SKU库存不存在: " + skuId);
        }
        return stock;
    }

    private List<StockReservationItem> loadItems(Long reservationId) {
        List<StockReservationItem> items = reservationItemMapper.selectByReservationId(reservationId);
        if (items.isEmpty()) {
            throw new ReservationException(Status.Code.INTERNAL, "预扣单缺少库存明细");
        }
        return items;
    }

    private static void requireReserved(StockReservation reservation, String operation) {
        if (reservation.getStatus() != StockReservation.Status.RESERVED) {
            throw new ReservationException(Status.Code.FAILED_PRECONDITION,
                    "当前状态不允许" + operation + ": " + reservation.getStatus());
        }
    }

    private static void requireStockInvariant(StockMain stock) {
        if (stock.getTotal() == null || stock.getAvailable() == null || stock.getReserved() == null
                || stock.getAvailable() < 0 || stock.getReserved() < 0
                || stock.getTotal() != (long) stock.getAvailable() + stock.getReserved()) {
            throw new ReservationException(Status.Code.ABORTED,
                    "SKU库存数据不满足total=available+reserved: " + stock.getSkuId());
        }
    }

    private void insertLog(StockReservation reservation, StockReservationItem item,
                           StockLog.Operation operation, int availableDelta, int reservedDelta,
                           int beforeAvailable, int afterAvailable) {
        StockLog stockLog = new StockLog();
        stockLog.setSkuId(item.getSkuId());
        stockLog.setReservationNo(reservation.getReservationNo());
        stockLog.setOperation(operation);
        stockLog.setAvailableDelta(availableDelta);
        stockLog.setReservedDelta(reservedDelta);
        stockLog.setBeforeAvailable(beforeAvailable);
        stockLog.setAfterAvailable(afterAvailable);
        stockLogMapper.insertLog(stockLog);
    }

    private static void verifyRepeatedRequest(NormalizedRequest request, StockReservation reservation,
                                              List<StockReservationItem> persistedItems) {
        boolean sameItems = request.requestHash().equals(reservation.getRequestHash())
                && request.requestHash().equals(fingerprint(persistedItems));
        if (!Objects.equals(request.orderNo(), reservation.getOrderNo())
                || !Objects.equals(request.expireAt(), reservation.getExpireAt())
                || !sameItems) {
            throw new ReservationException(Status.Code.ALREADY_EXISTS,
                    "reservationNo已用于不同的预扣请求");
        }
    }

    private static NormalizedRequest normalize(ReserveStockRequest request) {
        String reservationNo = requiredText(request.getReservationNo(), "reservationNo",
                MAX_RESERVATION_NO_LENGTH);
        String orderNo = requiredText(request.getOrderNo(), "orderNo", MAX_ORDER_NO_LENGTH);
        String expireAtText = requiredText(request.getExpireAt(), "expireAt", 64);
        LocalDateTime expireAt = parseExpireAt(expireAtText);
        if (!expireAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ReservationException(Status.Code.INVALID_ARGUMENT, "expireAt必须晚于当前UTC时间");
        }

        if (request.getItemsCount() < 1 || request.getItemsCount() > 100) {
            throw new ReservationException(Status.Code.INVALID_ARGUMENT, "items必须包含1到100项");
        }
        Map<Long, Integer> mergedItems = new TreeMap<>();
        for (ReservationItem item : request.getItemsList()) {
            if (item.getSkuId() <= 0) {
                throw new ReservationException(Status.Code.INVALID_ARGUMENT, "skuId必须为正数");
            }
            if (item.getQuantity() <= 0) {
                throw new ReservationException(Status.Code.INVALID_ARGUMENT, "quantity必须为正数");
            }
            mergedItems.merge(item.getSkuId(), item.getQuantity(), (current, added) -> {
                long merged = (long) current + added;
                if (merged > Integer.MAX_VALUE) {
                    throw new ReservationException(Status.Code.INVALID_ARGUMENT,
                            "重复SKU合并后的quantity超出范围: " + item.getSkuId());
                }
                return (int) merged;
            });
        }

        List<StockReservationItem> items = mergedItems.entrySet().stream()
                .map(entry -> {
                    StockReservationItem item = new StockReservationItem();
                    item.setSkuId(entry.getKey());
                    item.setQuantity(entry.getValue());
                    return item;
                })
                .toList();
        return new NormalizedRequest(reservationNo, orderNo, expireAt, fingerprint(items), items);
    }

    private static LocalDateTime parseExpireAt(String value) {
        try {
            Instant instant = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant()
                    .truncatedTo(ChronoUnit.MICROS);
            LocalDateTime expireAt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            if (expireAt.getYear() < 1000 || expireAt.getYear() > 9999) {
                throw new DateTimeException("expireAt超出MySQL DATETIME支持范围");
            }
            return expireAt;
        } catch (DateTimeException exception) {
            throw new ReservationException(Status.Code.INVALID_ARGUMENT,
                    "expireAt必须为带时区的ISO 8601时间");
        }
    }

    /**
     * 指纹只依赖排序、合并后的SKU和数量，不受请求明细顺序及重复项影响。
     */
    private static String fingerprint(List<StockReservationItem> items) {
        StringBuilder canonical = new StringBuilder();
        items.stream()
                .sorted((left, right) -> Long.compare(left.getSkuId(), right.getSkuId()))
                .forEach(item -> canonical.append(item.getSkuId())
                        .append(':')
                        .append(item.getQuantity())
                        .append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ReservationException(Status.Code.INVALID_ARGUMENT, field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ReservationException(Status.Code.INVALID_ARGUMENT,
                    field + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static ReserveStockResponse reserveResponse(StockReservation reservation,
                                                        List<StockReservationItem> items) {
        return ReserveStockResponse.newBuilder()
                .setReservationNo(reservation.getReservationNo())
                .setOrderNo(reservation.getOrderNo())
                .setStatus(reservation.getStatus().name())
                .addAllItems(toProtoItems(items))
                .build();
    }

    private static ConfirmReservationResponse confirmResponse(StockReservation reservation,
                                                              List<StockReservationItem> items) {
        return ConfirmReservationResponse.newBuilder()
                .setReservationNo(reservation.getReservationNo())
                .setOrderNo(reservation.getOrderNo())
                .setStatus(StockReservation.Status.CONFIRMED.name())
                .addAllItems(toProtoItems(items))
                .build();
    }

    private static ReleaseReservationResponse releaseResponse(StockReservation reservation,
                                                              List<StockReservationItem> items) {
        return ReleaseReservationResponse.newBuilder()
                .setReservationNo(reservation.getReservationNo())
                .setOrderNo(reservation.getOrderNo())
                .setStatus(StockReservation.Status.RELEASED.name())
                .addAllItems(toProtoItems(items))
                .build();
    }

    private static List<ReservationItem> toProtoItems(List<StockReservationItem> items) {
        return items.stream()
                .map(item -> ReservationItem.newBuilder()
                        .setSkuId(item.getSkuId())
                        .setQuantity(item.getQuantity())
                        .build())
                .toList();
    }

    private record NormalizedRequest(
            String reservationNo,
            String orderNo,
            LocalDateTime expireAt,
            String requestHash,
            List<StockReservationItem> items
    ) {
    }
}
