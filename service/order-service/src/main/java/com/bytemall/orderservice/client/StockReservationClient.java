package com.bytemall.orderservice.client;

import com.bytemall.stock.proto.ReleaseReservationRequest;
import com.bytemall.stock.proto.ReservationItem;
import com.bytemall.stock.proto.ReserveStockRequest;
import com.bytemall.stock.proto.StockServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存服务客户端，下单整体预扣库存并在创建失败时回补。
 */
@Slf4j
@Component
public class StockReservationClient {

    private static final long RPC_DEADLINE_SECONDS = 3;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final StockServiceGrpc.StockServiceBlockingStub stub;

    public StockReservationClient(
            @Value("${STOCK_GRPC_HOST:localhost}") String host,
            @Value("${STOCK_GRPC_PORT:9091}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = StockServiceGrpc.newBlockingStub(channel);
    }

    /** 整体预扣订单库存；任一SKU不足时抛出409，由调用方终止下单。 */
    public void reserve(String reservationNo, String orderNo, Instant expireAt, List<Item> items) {
        ReserveStockRequest.Builder request = ReserveStockRequest.newBuilder()
                .setReservationNo(reservationNo)
                .setOrderNo(orderNo)
                .setExpireAt(expireAt.toString());
        for (Item item : items) {
            request.addItems(ReservationItem.newBuilder()
                    .setSkuId(item.skuId())
                    .setQuantity(item.quantity())
                    .build());
        }
        try {
            stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .reserveStock(request.build());
        } catch (StatusRuntimeException exception) {
            throw mapReserveFailure(exception);
        }
    }

    /** 创建流程失败后的补偿回补；释放失败只记录日志，不覆盖原始错误。 */
    public void releaseQuietly(String reservationNo, String reason) {
        try {
            stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .releaseReservation(ReleaseReservationRequest.newBuilder()
                            .setReservationNo(reservationNo)
                            .setReason(reason)
                            .build());
        } catch (StatusRuntimeException exception) {
            log.error("释放库存预扣单失败，reservationNo={}, gRPC状态: {}",
                    reservationNo, exception.getStatus().getCode());
        }
    }

    private static ResponseStatusException mapReserveFailure(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        String description = exception.getStatus().getDescription();
        return switch (code) {
            case RESOURCE_EXHAUSTED -> new ResponseStatusException(
                    HttpStatus.CONFLICT, description == null ? "商品库存不足" : description);
            case NOT_FOUND -> new ResponseStatusException(
                    HttpStatus.CONFLICT, description == null ? "SKU库存不存在" : description);
            case ALREADY_EXISTS, ABORTED, FAILED_PRECONDITION -> new ResponseStatusException(
                    HttpStatus.CONFLICT, description == null ? "库存预扣冲突，请重试" : description);
            case INVALID_ARGUMENT -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, description == null ? "库存预扣参数不合法" : description);
            default -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "库存服务暂时不可用，无法预扣库存", exception);
        };
    }

    /** 预扣明细：SKU使用数值ID，数量为正整数。 */
    public record Item(long skuId, int quantity) {
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("库存服务gRPC通道未在{}秒内关闭，强制终止", SHUTDOWN_WAIT_SECONDS);
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
