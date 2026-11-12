package com.bytemall.productservice.products;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.mapper.ProductSpuMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;


    private final ObjectMapper objectMapper;
    private final ProductStockClient productStockClient;

    // 分页获取商品列表
    public ProductResponses.ProductPage getProductsList(int page, int size) {
        // 计算偏移量
        long offset = (page - 1L) * size;
        long total = productSpuMapper.countOnSale();
        // 获取在售spu列表
        List<ProductSpu> spuList = productSpuMapper.selectOnSalePage(size, offset);


        //获取spu id 列表
        List<Long> spuIds = spuList.stream().map(ProductSpu::getId).toList();

        // 相当于写sql：SELECT spu_id, price FROM product_sku WHERE spu_id IN (101, 102)  AND enabled = TRUE;
        Map<Long, LongSummaryStatistics> pricesBySpu = productSkuMapper
                .selectList(Wrappers.<ProductSku>lambdaQuery()
                        .select(ProductSku::getSpuId, ProductSku::getPrice)
                        .in(ProductSku::getSpuId, spuIds)
                        .eq(ProductSku::getEnabled, true))
                .stream()
                // 执行分组和聚合，最终得到按 SPU ID 查找的 Map。
                .collect(Collectors.groupingBy(
                        // 指定分组键。同一个 spuId 的 SKU 放在同一组。
                        ProductSku::getSpuId,
                        // 对每组的 price 做统计，后续用 getMin() 和 getMax()。
                        Collectors.summarizingLong(ProductSku::getPrice)
                ));
        //  这段代码是为了给当前页的每件商品选一张封面图，得到一个 商品 SPU ID → 封面图片 URL 的 Map。
        Map<Long, String> coverBySpu = productImageMapper
                .selectList(Wrappers.<ProductImage>lambdaQuery()
                        .select(ProductImage::getSpuId, ProductImage::getUrl)
                        .in(ProductImage::getSpuId, spuIds)
                        // 主图优先。常见的 MySQL 布尔列中，true 排在 false 前面。
                        .orderByDesc(ProductImage::getPrimaryImage)
                        // 主图标记相同时，先按展示顺序排，再用图片 ID 打破并列。
                        .orderByAsc(ProductImage::getSortOrder, ProductImage::getId))
                .stream()
                // 把图片列表转成「SPU ID → 封面 URL」。
                .collect(Collectors.toMap(
                        // 用图片所属的 SPU ID 作为键。
                        ProductImage::getSpuId,
                        // 用图片 URL 作为值。
                        ProductImage::getUrl,
                        // 同一商品遇到多张图时保留先遇到的那张。
                        (first, next) -> first
                ));

        List<ProductResponses.ProductSummary> items = spuList.stream().map(spu -> {
            LongSummaryStatistics prices = pricesBySpu.get(spu.getId());
            return new ProductResponses.ProductSummary(
                    spu.getId().toString(),
                    spu.getMerchantId().toString(),
                    spu.getTitle(),
                    spu.getSubtitle(),
                    spu.getBrand(),
                    spu.getCategoryId().toString(),
                    coverBySpu.getOrDefault(spu.getId(), ""),
                    prices == null ? 0L : prices.getMin(),
                    prices == null ? 0L : prices.getMax(),
                    spu.getSalesCount() == null ? 0L : spu.getSalesCount(),
                    spu.getStatus(),
                    spu.getPublishedAt() == null ? null : spu.getPublishedAt().toInstant(ZoneOffset.UTC).toString()
            );
        }).toList();
        return new ProductResponses.ProductPage(page, size, total, items);
    }


    public Optional<ProductResponses.ProductDetail> getProductDetail(long spuId) {
        ProductSpu spu = productSpuMapper.selectOne(
                Wrappers.<ProductSpu>lambdaQuery()
                        .eq(ProductSpu::getId, spuId)
                        .eq(ProductSpu::getStatus, "ON_SALE"));
        if (spu == null) {
            return Optional.empty();
        }


        List<ProductResponses.Image> images = productImageMapper.selectList(
                Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getSpuId, spuId)
                        //  按 is_primary 降序，主图（true）排在普通图（false）前面。
                        .orderByDesc(ProductImage::getPrimaryImage)
                        //  在主图标记相同的图片之间，先按 sort_order 从小到大排。如果 sort_order 也一样，就按 id 从小到大排。
                        .orderByAsc(ProductImage::getSortOrder, ProductImage::getId)
        ).stream().map(image -> new ProductResponses.Image(
                image.getUrl(),
                image.getAltText(),
                image.getSortOrder(),
                image.getPrimaryImage()
        )).toList();


        List<ProductSku> enabledSkus = productSkuMapper.selectList(
                Wrappers.<ProductSku>lambdaQuery()
                        .eq(ProductSku::getSpuId, spuId)
                        .eq(ProductSku::getEnabled, true)
                        .orderByAsc(ProductSku::getId)
        );

        Map<Long, Integer> stocks = productStockClient.getAvailableStocks(
                enabledSkus.stream().map(ProductSku::getId).toList());
        List<ProductResponses.Sku> skus = enabledSkus.stream()
                .map(sku -> new ProductResponses.Sku(
                        sku.getId().toString(),
                        sku.getSkuCode(),
                        readSpecs(sku),
                        sku.getPrice(),
                        sku.getOriginalPrice(),
                        stocks.getOrDefault(sku.getId(), 0),
                        true))
                .toList();

        LongSummaryStatistics prices = skus.stream()
                .mapToLong(ProductResponses.Sku::price)
                .summaryStatistics();
        if (skus.isEmpty()) {
            // ProductDetail.skus 要求至少一项。这类商品在公开详情中不可见。
            return Optional.empty();
        }

        return Optional.of(new ProductResponses.ProductDetail(
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
                spu.getPublishedAt().toString(),
                spu.getDescriptionHtml(),
                images,
                skus,
                spu.getVersion()
        ));
    };


    private Map<String, String> readSpecs(ProductSku sku) {
        try {
            return objectMapper.readValue(
                    sku.getSpecJson(), new TypeReference<Map<String, String>>() {
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法解析 SKU 规格，skuId=" + sku.getId(), e);
        }
    }


}

