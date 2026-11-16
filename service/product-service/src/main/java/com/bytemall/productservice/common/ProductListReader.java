package com.bytemall.productservice.common;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.products.ProductResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductListReader {
    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;

    public enum SkuScope {
        ENABLED_ONLY,
        ALL
    }

    public List<ProductResponses.ProductSummary> read(List<ProductSpu> spuList, SkuScope skuScope) {

        //获取spu id 列表
        List<Long> spuIds = spuList.stream().map(ProductSpu::getId).toList();
        // 相当于写sql：SELECT spu_id, price FROM product_sku WHERE spu_id IN (101, 102)  AND enabled = TRUE;
        Map<Long, LongSummaryStatistics> pricesBySpu = productSkuMapper
                .selectList(Wrappers.<ProductSku>lambdaQuery()
                        .select(ProductSku::getSpuId, ProductSku::getPrice)
                        .in(ProductSku::getSpuId, spuIds)
                        .eq(skuScope == SkuScope.ENABLED_ONLY, ProductSku::getEnabled, true))
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
        return items;
    }

}
