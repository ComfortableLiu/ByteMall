package com.bytemall.productservice.grpc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.product.proto.GetSkuBatchRequest;
import com.bytemall.product.proto.GetSkuBatchResponse;
import com.bytemall.product.proto.GetSkuBriefsRequest;
import com.bytemall.product.proto.GetSkuBriefsResponse;
import com.bytemall.product.proto.ProductServiceGrpc;
import com.bytemall.product.proto.ProductSkuInfo;
import com.bytemall.product.proto.SkuBrief;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.mapper.ProductSpuMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品gRPC服务端实现，为商家库存页批量补齐SKU摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;
    private final ProductImageMapper productImageMapper;

    @Override
    public void getSkuBriefs(
            GetSkuBriefsRequest request,
            StreamObserver<GetSkuBriefsResponse> responseObserver
    ) {
        try {
            responseObserver.onNext(querySkuBriefs(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            log.error("Failed to get SKU briefs", exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("查询SKU摘要失败")
                    .asRuntimeException());
        }
    }

    @Override
    public void getSkuBatch(
            GetSkuBatchRequest request,
            StreamObserver<GetSkuBatchResponse> responseObserver
    ) {
        try {
            responseObserver.onNext(querySkuBatch(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            log.error("Failed to get SKU batch", exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("查询SKU快照失败")
                    .asRuntimeException());
        }
    }

    /**
     * 下单快照查询：SKU、SPU和主图各查一次，带出现价与上下架状态，不做过滤。
     */
    private GetSkuBatchResponse querySkuBatch(GetSkuBatchRequest request) {
        if (request.getSkuIdsCount() == 0 || request.getSkuIdsCount() > 100) {
            throw new IllegalArgumentException("skuIds必须包含1到100项");
        }

        Set<Long> skuIds = request.getSkuIdsList().stream()
                .map(ProductGrpcService::parseSkuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductSku> skusById = productSkuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        Set<Long> spuIds = skusById.values().stream()
                .map(ProductSku::getSpuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductSpu> spusById = spuIds.isEmpty()
                ? Map.of()
                : productSpuMapper.selectByIds(spuIds).stream()
                        .collect(Collectors.toMap(ProductSpu::getId, Function.identity()));
        Map<Long, String> primaryImagesBySpuId = queryPrimaryImages(spuIds);

        GetSkuBatchResponse.Builder response = GetSkuBatchResponse.newBuilder();
        List<String> missingSkuIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            ProductSku sku = skusById.get(skuId);
            ProductSpu spu = sku == null ? null : spusById.get(sku.getSpuId());
            if (sku == null || spu == null) {
                missingSkuIds.add(skuId.toString());
                continue;
            }
            response.addItems(ProductSkuInfo.newBuilder()
                    .setSkuId(sku.getId().toString())
                    .setSpuId(spu.getId().toString())
                    .setMerchantId(spu.getMerchantId() == null ? "" : spu.getMerchantId().toString())
                    .setTitle(nullToEmpty(spu.getTitle()))
                    .setSubtitle(nullToEmpty(spu.getSubtitle()))
                    .setBrand(nullToEmpty(spu.getBrand()))
                    .setCategoryId(spu.getCategoryId() == null ? "" : spu.getCategoryId().toString())
                    .setCoverImage(primaryImagesBySpuId.getOrDefault(spu.getId(), ""))
                    .setSpecJson(nullToEmpty(sku.getSpecJson()))
                    .setPrice(sku.getPrice() == null ? 0L : sku.getPrice())
                    .setOriginalPrice(sku.getOriginalPrice() == null ? 0L : sku.getOriginalPrice())
                    .setEnabled(Boolean.TRUE.equals(sku.getEnabled()))
                    .setStatus(nullToEmpty(spu.getStatus()))
                    .setVersion(spu.getVersion() == null ? 0L : spu.getVersion())
                    .build());
        }
        response.addAllMissingSkuIds(missingSkuIds);
        log.info("Getting SKU batch for {} SKUs, found {}, missing {}",
                skuIds.size(), response.getItemsCount(), missingSkuIds.size());
        return response.build();
    }

    /**
     * SKU、SPU和图片各查询一次，不按上下架状态过滤，并按请求顺序组装结果。
     */
    private GetSkuBriefsResponse querySkuBriefs(GetSkuBriefsRequest request) {
        if (request.getSkuIdsCount() == 0 || request.getSkuIdsCount() > 100) {
            throw new IllegalArgumentException("skuIds必须包含1到100项");
        }

        Set<Long> skuIds = request.getSkuIdsList().stream()
                .map(ProductGrpcService::parseSkuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductSku> skusById = productSkuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));

        Set<Long> spuIds = skusById.values().stream()
                .map(ProductSku::getSpuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductSpu> spusById = spuIds.isEmpty()
                ? Map.of()
                : productSpuMapper.selectByIds(spuIds).stream()
                        .collect(Collectors.toMap(ProductSpu::getId, Function.identity()));
        Map<Long, String> primaryImagesBySpuId = queryPrimaryImages(spuIds);

        GetSkuBriefsResponse.Builder response = GetSkuBriefsResponse.newBuilder();
        List<String> missingSkuIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            ProductSku sku = skusById.get(skuId);
            ProductSpu spu = sku == null ? null : spusById.get(sku.getSpuId());
            if (sku == null || spu == null) {
                missingSkuIds.add(skuId.toString());
                continue;
            }
            response.addItems(SkuBrief.newBuilder()
                    .setSkuId(sku.getId().toString())
                    .setSpuId(spu.getId().toString())
                    .setMerchantId(spu.getMerchantId().toString())
                    .setProductTitle(nullToEmpty(spu.getTitle()))
                    .setProductImage(primaryImagesBySpuId.getOrDefault(spu.getId(), ""))
                    .setSkuCode(nullToEmpty(sku.getSkuCode()))
                    .setSpecJson(nullToEmpty(sku.getSpecJson()))
                    .setEnabled(Boolean.TRUE.equals(sku.getEnabled()))
                    .setStatus(nullToEmpty(spu.getStatus()))
                    .setVersion(spu.getVersion() == null ? 0L : spu.getVersion())
                    .build());
        }
        response.addAllMissingSkuIds(missingSkuIds);
        log.info("Getting SKU briefs for {} SKUs, found {}, missing {}",
                skuIds.size(), response.getItemsCount(), missingSkuIds.size());
        return response.build();
    }

    /**
     * 主图优先，其次按展示顺序和图片ID排序；每个SPU只保留第一张图。
     */
    private Map<Long, String> queryPrimaryImages(Set<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return Map.of();
        }
        return productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .in(ProductImage::getSpuId, spuIds)
                        .orderByDesc(ProductImage::getPrimaryImage)
                        .orderByAsc(ProductImage::getSortOrder, ProductImage::getId))
                .stream()
                .collect(Collectors.toMap(
                        ProductImage::getSpuId,
                        ProductImage::getUrl,
                        (first, next) -> first
                ));
    }

    /**
     * 拦截空值、非数字和超出Long范围的SKU ID。
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
