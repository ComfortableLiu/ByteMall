package com.bytemall.stockservice.client;

import com.bytemall.user.proto.GetUserContextRequest;
import com.bytemall.user.proto.GetUserContextResponse;
import com.bytemall.user.proto.UserServiceGrpc;
import com.bytemall.user.proto.ValidateMerchantRequest;
import com.bytemall.user.proto.ValidateMerchantResponse;
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

@Slf4j
@Component
public class UserContextClient {
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final long RPC_DEADLINE_SECONDS = 2;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserContextClient(
            @Value("${USER_GRPC_HOST:localhost}") String host,
            @Value("${USER_GRPC_PORT:9093}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = UserServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 用户状态和商家绑定必须同时有效，不能只信任Token中的用户ID。
     */
    public Long requireActiveMerchant(Long userId) {
        if (userId == null || userId <= 0) {
            throw forbidden("用户身份无效，无法操作商家库存");
        }

        GetUserContextResponse context;
        try {
            context = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getUserContext(GetUserContextRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
        } catch (StatusRuntimeException exception) {
            throw rpcFailure("获取用户上下文", exception);
        }

        if (!ACTIVE_STATUS.equals(context.getStatus())) {
            throw forbidden("用户状态不是ACTIVE，无法操作商家库存");
        }
        long merchantId = context.getMerchantId();
        if (merchantId <= 0) {
            throw forbidden("当前用户没有有效的商家绑定");
        }

        ValidateMerchantResponse validation;
        try {
            validation = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .validateMerchant(ValidateMerchantRequest.newBuilder()
                            .setUserId(userId)
                            .setMerchantId(merchantId)
                            .build());
        } catch (StatusRuntimeException exception) {
            throw rpcFailure("校验商家身份", exception);
        }

        if (!validation.getValid()) {
            String reason = validation.getReason().isBlank()
                    ? "用户与商家绑定无效"
                    : validation.getReason();
            throw forbidden("商家校验未通过: " + reason);
        }
        if (!ACTIVE_STATUS.equals(validation.getMerchantStatus())) {
            throw forbidden("商家状态不是ACTIVE，无法操作库存");
        }
        if (validation.getMerchantId() != merchantId) {
            throw forbidden("商家校验结果与用户绑定不一致");
        }
        return merchantId;
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException rpcFailure(String action, StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        if (code == Status.Code.NOT_FOUND) {
            return forbidden("用户不存在或无法确认商家身份");
        }
        if (code == Status.Code.PERMISSION_DENIED || code == Status.Code.UNAUTHENTICATED) {
            return forbidden("用户服务拒绝了商家身份校验");
        }
        log.warn("{}失败，gRPC状态: {}", action, code);
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                action + "失败，用户服务暂时不可用",
                exception
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
