package com.bytemall.orderservice.client;

import com.bytemall.orderservice.orders.OrderResponses;
import com.bytemall.user.proto.GetAddressRequest;
import com.bytemall.user.proto.GetAddressResponse;
import com.bytemall.user.proto.UserServiceGrpc;
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

import java.util.concurrent.TimeUnit;

/**
 * 用户服务客户端，读取买家地址并生成订单地址快照。
 */
@Slf4j
@Component
public class UserAddressClient {

    private static final long RPC_DEADLINE_SECONDS = 2;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserAddressClient(
            @Value("${USER_GRPC_HOST:localhost}") String host,
            @Value("${USER_GRPC_PORT:9093}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = UserServiceGrpc.newBlockingStub(channel);
    }

    /** 地址必须属于当前买家，不存在或越权都按无效选择处理。 */
    public OrderResponses.AddressSnapshot getAddress(String addressId, Long userId) {
        GetAddressResponse response;
        try {
            response = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getAddress(GetAddressRequest.newBuilder()
                            .setAddressId(addressId)
                            .setUserId(String.valueOf(userId))
                            .build());
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的收货地址");
            }
            log.warn("查询收货地址失败，gRPC状态: {}", exception.getStatus().getCode());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "用户服务暂时不可用，无法校验收货地址",
                    exception
            );
        }
        return new OrderResponses.AddressSnapshot(
                response.getReceiverName(),
                response.getReceiverMobile(),
                response.getProvince(),
                response.getCity(),
                response.getDistrict(),
                response.getDetailAddress(),
                response.getPostalCode(),
                response.getIsDefault()
        );
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("用户服务gRPC通道未在{}秒内关闭，强制终止", SHUTDOWN_WAIT_SECONDS);
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
