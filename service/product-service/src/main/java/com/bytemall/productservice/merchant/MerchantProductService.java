package com.bytemall.productservice.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.common.model.dto.ApiResponse;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.CategoryMapper;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.mapper.ProductSpuMapper;
import com.bytemall.productservice.products.ProductResponses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantProductService {

    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;


    //1. @Transactional 给这个方法划出事务边界。后面把 SKU 和图片写入也加进来，就可以一起提交、一起回滚。
    @Transactional
    public Long createDraft(Long merchantId, CreateProductRequest request) {
        if (request.skus() == null || request.skus().isEmpty()) {
            throw new IllegalArgumentException("商品至少需要一个 SKU");
        }
        // request.categoryId() 是前端传来的字符串，例如 "1"。
        Long categoryId = Long.parseLong(request.categoryId());
        // new ProductSpu() 创建一个 Java 对象。每个 set... 调用把一个值填进去。
        ProductSpu spu = new ProductSpu();
        spu.setMerchantId(merchantId);
        spu.setCategoryId(categoryId);
        spu.setTitle(request.title());
        spu.setSubtitle(request.subtitle());
        spu.setBrand(request.brand());
        spu.setDescriptionHtml(request.descriptionHtml());
        spu.setStatus("DRAFT");
        spu.setVersion(0L);
        // insert(spu) 才会执行数据库写入。`insert` 的返回值是影响行数，商品 ID 要从 spu.getId() 取。
        productSpuMapper.insert(spu);
        for (CreateProductRequest.SkuRequest input : request.skus()) {
            Map<String, String> sortedSpecs = new TreeMap<>();
            if (input.specs() != null) {
                sortedSpecs.putAll(input.specs());
            }

            String specJson;
            try {
                specJson = objectMapper.writeValueAsString(sortedSpecs);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法序列化 SKU 规格", e);
            }

            ProductSku sku = new ProductSku();
            sku.setSpuId(spu.getId());
            sku.setSkuCode(input.skuCode());
            sku.setSpecJson(specJson);
            sku.setSpecHash(hashSpecs(specJson));
            sku.setPrice(input.price());
            sku.setOriginalPrice(input.originalPrice());
            sku.setEnabled(input.enabled() == null ? true : input.enabled());

            productSkuMapper.insert(sku);
        }
        if (request.images() != null) {
            for (CreateProductRequest.ImageRequest input : request.images()) {
                ProductImage image = new ProductImage();
                image.setSpuId(spu.getId());
                image.setUrl(input.url());
                image.setAltText(input.altText() == null ? "" : input.altText());
                image.setSortOrder(input.sortOrder() == null ? 0 : input.sortOrder());
                image.setPrimaryImage(Boolean.TRUE.equals(input.isPrimary()));

                productImageMapper.insert(image);
            }
        }
        return spu.getId();
    }

    private byte[] hashSpecs(String specJson) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(specJson.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算 SKU 规格哈希", e);
        }
    }

    public ProductResponses.ProductPage getMerchantProductList(
            Long merchantId, int page, int size, String status
    ) {
        long offset = (page - 1L) * size;
        long total = productSpuMapper.countMerchantProducts(merchantId, status);
        if (offset >= total) {
            return new ProductResponses.ProductPage(page, size, total, List.of());
        }
        List<ProductSpu> productSpuList = productSpuMapper.selectMerchantProductPage(merchantId, size, offset, status);


        //获取spu id 列表
        List<Long> spuIds = productSpuList.stream().map(ProductSpu::getId).toList();

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

        List<ProductResponses.ProductSummary> items = productSpuList.stream().map(spu -> {
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

}