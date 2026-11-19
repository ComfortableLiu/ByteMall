package com.bytemall.orderservice.client;

import com.bytemall.product.proto.GetSkuBatchRequest;
import com.bytemall.product.proto.GetSkuBatchResponse;
import com.bytemall.product.proto.ProductServiceGrpc;
import com.bytemall.product.proto.ProductSkuInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务客户端，下单时批量获取SKU现价与商品快照。
 */
@Slf4j
@Component
public class ProductSkuClient {

    private static final long RPC_DEADLINE_SECONDS = 2;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final ProductServiceGrpc.ProductServiceBlockingStub stub;

    public ProductSkuClient(
            @Value("${PRODUCT_GRPC_HOST:localhost}") String host,
            @Value("${PRODUCT_GRPC_PORT:9094}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = ProductServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 按SKU ID批量查询商品快照，保持请求顺序去重；缺失项由调用方决定处理方式。
     */
    public Map<String, ProductSkuInfo> getSkuBatch(List<String> skuIds) {
        Set<String> uniqueIds = new LinkedHashSet<>(skuIds);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        if (uniqueIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次最多查询100个SKU");
        }
        GetSkuBatchResponse response;
        try {
            response = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getSkuBatch(GetSkuBatchRequest.newBuilder()
                            .addAllSkuIds(new ArrayList<>(uniqueIds))
                            .build());
        } catch (StatusRuntimeException exception) {
            log.warn("批量查询SKU快照失败，gRPC状态: {}", exception.getStatus().getCode());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "商品服务暂时不可用，无法校验商品信息",
                    exception
            );
        }
        Map<String, ProductSkuInfo> bySkuId = new LinkedHashMap<>();
        for (ProductSkuInfo info : response.getItemsList()) {
            if (uniqueIds.contains(info.getSkuId())) {
                bySkuId.putIfAbsent(info.getSkuId(), info);
            }
        }
        return bySkuId;
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("商品服务gRPC通道未在{}秒内关闭，强制终止", SHUTDOWN_WAIT_SECONDS);
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
