package com.bytemall.orderservice.client;

import com.bytemall.payment.proto.CloseOrderRequest;
import com.bytemall.payment.proto.CloseOrderResponse;
import com.bytemall.payment.proto.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 支付服务客户端，订单取消时关闭该订单当前的待支付支付单。
 */
@Slf4j
@Component
public class PaymentCloseClient {

    private static final long RPC_DEADLINE_SECONDS = 3;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;

    public PaymentCloseClient(
            @Value("${PAY_GRPC_HOST:localhost}") String host,
            @Value("${PAY_GRPC_PORT:9092}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 关闭订单的待支付支付单；reason 为 pay-service 认可的关闭原因（如 ORDER_CANCELLED、PAYMENT_TIMEOUT）。
     * 重复关闭在支付侧幂等。失败只记录日志：订单状态是事实来源，遗留的待支付单由对账兜底。
     */
    public void closeQuietly(String orderNo, String reason) {
        try {
            CloseOrderResponse response = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .closeOrder(CloseOrderRequest.newBuilder()
                            .setOrderNo(orderNo)
                            .setReason(reason)
                            .build());
            log.info("订单{}的支付单已关闭: {}", orderNo, response.getPayNo());
        } catch (StatusRuntimeException exception) {
            log.error("关闭订单{}的支付单失败，gRPC状态: {}", orderNo, exception.getStatus().getCode());
        }
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("支付服务gRPC通道未在{}秒内关闭，强制终止", SHUTDOWN_WAIT_SECONDS);
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
