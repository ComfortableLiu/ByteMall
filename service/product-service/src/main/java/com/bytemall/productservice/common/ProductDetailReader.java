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
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;

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

        List<ProductResponses.Image> images = productImageMapper.selectList(
                        Wrappers.<ProductImage>lambdaQuery()
                                .eq(ProductImage::getSpuId, spuId)
                                .orderByDesc(ProductImage::getPrimaryImage)
                                .orderByAsc(ProductImage::getSortOrder, ProductImage::getId))
                .stream()
                .map(image -> new ProductResponses.Image(
                        image.getUrl(),
                        image.getAltText(),
                        image.getSortOrder(),
                        image.getPrimaryImage()))
                .toList();

        Map<Long, Integer> stocks = productStockClient.getAvailableStocks(
                skuRows.stream().map(ProductSku::getId).toList());

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

        return Optional.of(new ProductResponses.ProductDetail(
                spuId.toString(),
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
                spu.getVersion()));
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