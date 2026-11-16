package com.bytemall.productservice.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.common.ProductDetailReader;
import com.bytemall.productservice.common.ProductListReader;
import com.bytemall.productservice.entity.ProductImage;
import com.bytemall.productservice.entity.ProductSku;
import com.bytemall.productservice.entity.ProductSpu;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class MerchantProductService {

    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;
    private final ObjectMapper objectMapper;

    private final ProductDetailReader productDetailReader;
    private final ProductListReader productListReader;

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

        return new ProductResponses.ProductPage(page, size, total, productListReader.read(productSpuList, ProductListReader.SkuScope.ALL));
    }


    public Optional<ProductResponses.ProductDetail> getProductDetail(Long merchantId, Long spuId) {
        ProductSpu spu = productSpuMapper.selectOne(
                Wrappers.<ProductSpu>lambdaQuery()
                        .eq(ProductSpu::getId, spuId)
                        .eq(ProductSpu::getMerchantId, merchantId));
        if (spu == null) {
            return Optional.empty();
        }

        return productDetailReader.read(spu, ProductDetailReader.SkuScope.ALL);
    }

}