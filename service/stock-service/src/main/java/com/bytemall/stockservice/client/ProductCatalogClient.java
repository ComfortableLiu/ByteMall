package com.bytemall.stockservice.client;

import com.bytemall.product.proto.GetSkuBriefsRequest;
import com.bytemall.product.proto.GetSkuBriefsResponse;
import com.bytemall.product.proto.ProductServiceGrpc;
import com.bytemall.product.proto.SkuBrief;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ProductCatalogClient {
    private static final int BATCH_SIZE = 100;
    private static final long RPC_DEADLINE_SECONDS = 2;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ManagedChannel channel;
    private final ProductServiceGrpc.ProductServiceBlockingStub stub;
    private final ObjectMapper objectMapper;

    public ProductCatalogClient(
            @Value("${PRODUCT_GRPC_HOST:localhost}") String host,
            @Value("${PRODUCT_GRPC_PORT:9094}") int port,
            ObjectMapper objectMapper
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = ProductServiceGrpc.newBlockingStub(channel);
        this.objectMapper = objectMapper;
    }

    /**
     * 按商品服务限制分批查询；缺失SKU不放入结果，由调用方组装空摘要。
     */
    public Map<String, SkuSummary> getSkuBriefs(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (Long skuId : skuIds) {
            if (skuId != null) {
                uniqueIds.add(skuId.toString());
            }
        }
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        List<String> orderedIds = new ArrayList<>(uniqueIds);
        Map<String, SkuSummary> summaries = new LinkedHashMap<>();
        for (int start = 0; start < orderedIds.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, orderedIds.size());
            List<String> batch = orderedIds.subList(start, end);
            GetSkuBriefsResponse response;
            try {
                response = stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                        .getSkuBriefs(GetSkuBriefsRequest.newBuilder()
                                .addAllSkuIds(batch)
                                .build());
            } catch (StatusRuntimeException exception) {
                log.warn("批量查询SKU摘要失败，gRPC状态: {}", exception.getStatus().getCode());
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "商品服务暂时不可用，无法查询SKU摘要",
                        exception
                );
            }
            for (SkuBrief brief : response.getItemsList()) {
                // 只接受本请求涉及的SKU，避免异常下游响应污染库存列表。
                if (uniqueIds.contains(brief.getSkuId())) {
                    summaries.putIfAbsent(brief.getSkuId(), toSummary(brief));
                }
            }
        }
        return summaries;
    }

    private SkuSummary toSummary(SkuBrief brief) {
        return new SkuSummary(
                brief.getSpuId(),
                brief.getProductTitle(),
                brief.getProductImage(),
                brief.getSkuCode(),
                parseSpecs(brief.getSkuId(), brief.getSpecJson())
        );
    }

    private Map<String, String> parseSpecs(String skuId, String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(specJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("spec_json不是JSON对象");
            }
            Map<String, String> specs = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                String text;
                if (value == null || value.isNull()) {
                    text = "";
                } else if (value.isTextual()) {
                    text = value.textValue();
                } else if (value.isValueNode()) {
                    text = value.asText();
                } else {
                    text = value.toString();
                }
                specs.put(field.getKey(), text);
            }
            return Collections.unmodifiableMap(specs);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "商品服务返回了无法解析的SKU规格: " + skuId,
                    exception
            );
        }
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

    public record SkuSummary(
            String spuId,
            String productTitle,
            String productImage,
            String skuCode,
            Map<String, String> specs
    ) {
        public static SkuSummary empty() {
            return new SkuSummary("", "", "", "", Map.of());
        }
    }
}
