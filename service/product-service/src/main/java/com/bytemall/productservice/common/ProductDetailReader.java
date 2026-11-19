package com.bytemall.productservice.common;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.products.ProductResponses;
import com.bytemall.productservice.products.ProductStockClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductDetailReader {

    public enum SkuScope {
        ENABLED_ONLY,
        ALL
    }

    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductStockClient productStockClient;
    private final ObjectMapper objectMapper;

    public Optional<ProductResponses.ProductDetail> read(
            ProductSpu spu, SkuScope skuScope
    ) {
        Long spuId = spu.getId();

        List<ProductSku> skuRows = productSkuMapper.selectList(
                Wrappers.<ProductSku>lambdaQuery()
                        .eq(ProductSku::getSpuId, spuId)
                        .eq(skuScope == SkuScope.ENABLED_ONLY,
                                ProductSku::getEnabled, true)
                        .orderByAsc(ProductSku::getId));

        if (skuRows.isEmpty()) {
            return Optional.empty();
        }

        List<ProductImage> imageRows = productImageMapper.selectList(
                Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getSpuId, spuId)
                        .orderByDesc(ProductImage::getPrimaryImage)
                        .orderByAsc(ProductImage::getSortOrder, ProductImage::getId));

        Map<Long, Integer> stocks = productStockClient.getAvailableStocks(
                skuRows.stream().map(ProductSku::getId).toList());

        return Optional.of(assemble(spu, skuRows, imageRows, stocks));
    }

    /**
     * 批量装配商品详情：SKU、图片和库存各批量查询一次，避免逐个商品N+1。
     * 无SKU的商品跳过，保持与单个read相同的缺失语义。
     */
    public List<ProductResponses.ProductDetail> readBatch(List<ProductSpu> spuList, SkuScope skuScope) {
        if (spuList.isEmpty()) {
            return List.of();
        }
        List<Long> spuIds = spuList.stream().map(ProductSpu::getId).toList();

        Map<Long, List<ProductSku>> skusBySpu = productSkuMapper
                .selectList(Wrappers.<ProductSku>lambdaQuery()
                        .in(ProductSku::getSpuId, spuIds)
                        .eq(skuScope == SkuScope.ENABLED_ONLY, ProductSku::getEnabled, true)
                        .orderByAsc(ProductSku::getId))
                .stream()
                .collect(Collectors.groupingBy(ProductSku::getSpuId));

        Map<Long, List<ProductImage>> imagesBySpu = productImageMapper
                .selectList(Wrappers.<ProductImage>lambdaQuery()
                        .in(ProductImage::getSpuId, spuIds)
                        .orderByDesc(ProductImage::getPrimaryImage)
                        .orderByAsc(ProductImage::getSortOrder, ProductImage::getId))
                .stream()
                .collect(Collectors.groupingBy(ProductImage::getSpuId));

        List<Long> skuIds = skusBySpu.values().stream()
                .flatMap(List::stream)
                .map(ProductSku::getId)
                .toList();
        Map<Long, Integer> stocks = skuIds.isEmpty()
                ? Map.of()
                : productStockClient.getAvailableStocks(skuIds);

        List<ProductResponses.ProductDetail> details = new ArrayList<>();
        for (ProductSpu spu : spuList) {
            List<ProductSku> skuRows = skusBySpu.getOrDefault(spu.getId(), List.of());
            if (skuRows.isEmpty()) {
                continue;
            }
            details.add(assemble(spu, skuRows, imagesBySpu.getOrDefault(spu.getId(), List.of()), stocks));
        }
        return details;
    }

    private ProductResponses.ProductDetail assemble(
            ProductSpu spu,
            List<ProductSku> skuRows,
            List<ProductImage> imageRows,
            Map<Long, Integer> stocks
    ) {
        List<ProductResponses.Image> images = imageRows.stream()
                .map(image -> new ProductResponses.Image(
                        image.getUrl(),
                        image.getAltText(),
                        image.getSortOrder(),
                        image.getPrimaryImage()))
                .toList();

        List<ProductResponses.Sku> skus = skuRows.stream()
                .map(sku -> new ProductResponses.Sku(
                        sku.getId().toString(),
                        sku.getSkuCode(),
                        readSpecs(sku),
                        sku.getPrice(),
                        sku.getOriginalPrice(),
                        stocks.getOrDefault(sku.getId(), 0),
                        Boolean.TRUE.equals(sku.getEnabled())))
                .toList();

        LongSummaryStatistics prices = skus.stream()
                .mapToLong(ProductResponses.Sku::price)
                .summaryStatistics();

        return new ProductResponses.ProductDetail(
                spu.getId().toString(),
                spu.getMerchantId().toString(),
                spu.getTitle(),
                spu.getSubtitle(),
                spu.getBrand(),
                spu.getCategoryId().toString(),
                images.isEmpty() ? "" : images.get(0).url(),
                prices.getMin(),
                prices.getMax(),
                spu.getSalesCount(),
                spu.getStatus(),
                spu.getPublishedAt() == null ? null
                        : spu.getPublishedAt().toInstant(ZoneOffset.UTC).toString(),
                spu.getDescriptionHtml(),
                images,
                skus,
                spu.getVersion());
    }

    private Map<String, String> readSpecs(ProductSku sku) {
        try {
            return objectMapper.readValue(
                    sku.getSpecJson(), new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法解析 SKU 规格，skuId=" + sku.getId(), e);
        }
    }
}
