package com.bytemall.stockservice.grpc;

import com.bytemall.stock.proto.AvailableStock;
import com.bytemall.stock.proto.ConfirmReservationRequest;
import com.bytemall.stock.proto.ConfirmReservationResponse;
import com.bytemall.stock.proto.GetAvailableStocksRequest;
import com.bytemall.stock.proto.GetAvailableStocksResponse;
import com.bytemall.stock.proto.ReleaseReservationRequest;
import com.bytemall.stock.proto.ReleaseReservationResponse;
import com.bytemall.stock.proto.ReserveStockRequest;
import com.bytemall.stock.proto.ReserveStockResponse;
import com.bytemall.stock.proto.StockServiceGrpc;
import com.bytemall.stockservice.entity.StockMain;
import com.bytemall.stockservice.mapper.StockMainMapper;
import com.bytemall.stockservice.reservation.ReservationException;
import com.bytemall.stockservice.reservation.ReservationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 库存gRPC服务端实现，接收客户端请求并返回数据库中的最新可售库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockGrpcService extends StockServiceGrpc.StockServiceImplBase {

    private final StockMainMapper stockMainMapper;
    private final ReservationService reservationService;

    @Override
    public void getAvailableStocks(
            GetAvailableStocksRequest request,
            StreamObserver<GetAvailableStocksResponse> responseObserver
    ) {
        respond(responseObserver, "GetAvailableStocks", () -> queryAvailableStocks(request));
    }

    @Override
    public void reserveStock(
            ReserveStockRequest request,
            StreamObserver<ReserveStockResponse> responseObserver
    ) {
        respond(responseObserver, "ReserveStock", () -> reservationService.reserve(request));
    }

    @Override
    public void confirmReservation(
            ConfirmReservationRequest request,
            StreamObserver<ConfirmReservationResponse> responseObserver
    ) {
        respond(responseObserver, "ConfirmReservation",
                () -> reservationService.confirm(request.getReservationNo()));
    }

    @Override
    public void releaseReservation(
            ReleaseReservationRequest request,
            StreamObserver<ReleaseReservationResponse> responseObserver
    ) {
        respond(responseObserver, "ReleaseReservation",
                () -> reservationService.release(request.getReservationNo(), request.getReason()));
    }

    /**
     * 统一隔离业务异常和未知异常，避免把数据库细节直接暴露给调用方。
     */
    private <T> void respond(StreamObserver<T> responseObserver, String operation, Supplier<T> action) {
        try {
            responseObserver.onNext(action.get());
            responseObserver.onCompleted();
        } catch (ReservationException exception) {
            responseObserver.onError(Status.fromCode(exception.getCode())
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (DuplicateKeyException exception) {
            log.warn("{}触发唯一键屏障: {}", operation, exception.getMessage());
            responseObserver.onError(Status.ABORTED
                    .withDescription("库存操作与既有记录冲突，请核对后重试")
                    .asRuntimeException());
        } catch (PessimisticLockingFailureException exception) {
            log.warn("{}发生数据库锁冲突: {}", operation, exception.getMessage());
            responseObserver.onError(Status.ABORTED
                    .withDescription("库存操作发生锁冲突，请重试")
                    .asRuntimeException());
        } catch (Exception exception) {
            log.error("库存gRPC操作{}失败", operation, exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("库存服务内部错误")
                    .withCause(exception)
                    .asRuntimeException());
        }
    }

    /**
     * 批量查询库存：去重后一次查询数据库，找不到的SKU单独放入missingSkuIds。
     */
    private GetAvailableStocksResponse queryAvailableStocks(GetAvailableStocksRequest request) {
        if (request.getSkuIdsCount() == 0 || request.getSkuIdsCount() > 100) {
            throw new IllegalArgumentException("skuIds必须包含1到100项");
        }

        Set<Long> skuIds = request.getSkuIdsList().stream()
                .map(StockGrpcService::parseSkuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, StockMain> stocksById = stockMainMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(StockMain::getSkuId, Function.identity()));

        GetAvailableStocksResponse.Builder response = GetAvailableStocksResponse.newBuilder();
        List<String> missingSkuIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            StockMain stock = stocksById.get(skuId);
            if (stock == null) {
                missingSkuIds.add(skuId.toString());
                continue;
            }
            response.addItems(AvailableStock.newBuilder()
                    .setSkuId(skuId.toString())
                    .setAvailableStock(stock.getAvailable())
                    .setVersion(stock.getVersion())
                    .build());
        }
        response.addAllMissingSkuIds(missingSkuIds);
        log.info("Getting available stocks for {} SKUs, found {}, missing {}",
                skuIds.size(), response.getItemsCount(), missingSkuIds.size());
        return response.build();
    }

    /**
     * 将外部字符串ID安全转换为Long，同时拦截非数字和超出Long范围的输入。
     */
    private static long parseSkuId(String skuId) {
        if (skuId == null || !skuId.matches("\\d{1,19}")) {
            throw new IllegalArgumentException("skuId必须为1到19位数字字符串");
        }
        try {
            return Long.parseLong(skuId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("skuId超出Long范围: " + skuId, exception);
        }
    }
}
