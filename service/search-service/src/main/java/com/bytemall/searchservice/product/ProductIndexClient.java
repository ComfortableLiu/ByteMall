package com.bytemall.searchservice.product;

import com.bytemall.product.proto.GetIndexDocumentRequest;
import com.bytemall.product.proto.GetIndexDocumentResponse;
import com.bytemall.product.proto.ProductServiceGrpc;
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
 * 商品服务客户端：商品变更后拉取完整索引读模型。
 */
@Slf4j
@Component
public class ProductIndexClient {

    private static final long RPC_DEADLINE_SECONDS = 3;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final ProductServiceGrpc.ProductServiceBlockingStub stub;

    public ProductIndexClient(
            @Value("${PRODUCT_GRPC_HOST:localhost}") String host,
            @Value("${PRODUCT_GRPC_PORT:9094}") int port
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = ProductServiceGrpc.newBlockingStub(channel);
    }

    /** 商品不存在返回null，由调用方决定删除索引；商品服务故障按503抛出。 */
    public ProductIndexDocument getIndexDocument(String spuId) {
        GetIndexDocumentResponse response;
        try {
            response = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getIndexDocument(GetIndexDocumentRequest.newBuilder()
                            .setSpuId(spuId)
                            .build());
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalArgumentException("spuId不合法: " + spuId);
            }
            log.warn("查询索引文档失败，spuId={}, gRPC状态: {}", spuId, exception.getStatus().getCode());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "商品服务暂时不可用，无法同步索引",
                    exception
            );
        }
        if (!response.hasDocument() || response.getDocument().getSpuId().isBlank()) {
            return null;
        }
        com.bytemall.product.proto.ProductIndexDocument document = response.getDocument();
        return new ProductIndexDocument(
                document.getSpuId(),
                document.getMerchantId(),
                document.getCategoryId(),
                document.getBrand(),
                document.getCoverImage(),
                document.getTitle(),
                document.getSubtitle(),
                document.getDescriptionText(),
                document.getMinPrice(),
                document.getMaxPrice(),
                document.getSalesCount(),
                document.getStatus(),
                document.getPublishedAt(),
                document.getUpdatedAt(),
                document.getVersion()
        );
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
