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
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private final KafkaTemplate<String, String> kafkaTemplate;

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

    //    @Transactional 给这个方法划出事务边界。后面把 SKU 和图片写入也加进来，就可以一起提交、一起回滚。
    @Transactional
    public Optional<ProductResponses.ProductDetail> updateProductStatus(
            Long merchantId, Long spuId, UpdateProductStatusRequest request
    ) {
        ProductSpu spu = productSpuMapper.selectOne(
                Wrappers.<ProductSpu>lambdaQuery()
                        .eq(ProductSpu::getId, spuId)
                        .eq(ProductSpu::getMerchantId, merchantId));
        if (spu == null) {
            return Optional.empty();
        }
        String targetStatus = request.status().name();
//        判断要更新的状态是否和数据库里的状态不一致，不一致才走后续更新逻辑
        if (!targetStatus.equals(spu.getStatus())) {
            if ("DRAFT".equals(spu.getStatus()) && "OFF_SALE".equals(targetStatus)) {
                throw new StatusConflict("INVALID_PRODUCT_STATUS", "草稿商品需要先上架");
            }

            var update = Wrappers.<ProductSpu>lambdaUpdate()
                    .eq(ProductSpu::getId, spuId)
                    .eq(ProductSpu::getMerchantId, merchantId)
                    .eq(ProductSpu::getVersion, request.version())
                    .set(ProductSpu::getStatus, targetStatus)
                    .setSql("version = version + 1");
            if ("ON_SALE".equals(targetStatus) && spu.getPublishedAt() == null) {
                update.set(ProductSpu::getPublishedAt, LocalDateTime.now(ZoneOffset.UTC));
            }
            if (productSpuMapper.update(null, update) == 0) {
                throw new StatusConflict("PRODUCT_VERSION_CONFLICT", "商品已更新，请刷新后重试");
            }

            // 接入搜索同步时，在此处写入同一事务内的 ProductChanged Outbox 记录。

            publishProductChangedAfterCommit(spuId, request.version() + 1);
        }
        // 现有详情读取器在商品没有 SKU 时返回空。抛异常可回滚本次更新。
        return Optional.of(getProductDetail(merchantId, spuId)
                .orElseThrow(() -> new StatusConflict(
                        "PRODUCT_HAS_NO_SKU", "商品缺少 SKU，无法返回商品详情")));
    }

    static final class StatusConflict extends RuntimeException {
        private final String code;

        StatusConflict(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private void publishProductChangedAfterCommit(Long spuId, long version) {
        String productId = spuId.toString();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "ProductChanged",
                    "aggregateType", "PRODUCT",
                    "aggregateId", productId,
                    "version", version,
                    "occurredAt", Instant.now().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化商品变更事件", e);
        }
//registerSynchronization 把回调注册到当前事务，所以要在外层 @Transactional 方法执行期间调用。
        TransactionSynchronizationManager.registerSynchronization(
// 创建一个匿名实现，里面定义了要在事务的哪个阶段做什么。
//@Override 表示下面的方法覆盖了接口提供的 afterCommit()。
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kafkaTemplate.send("product-changed", productId, payload)
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        org.slf4j.LoggerFactory.getLogger(MerchantProductService.class)
                                                .error("商品变更事件发送失败，spuId={}，version={}",
                                                        productId, version, error);
                                    }
                                });
                    }
                });
    }

    @Transactional
    public Optional<ProductResponses.ProductDetail> updateProduct(Long merchantId, Long spuId, UpdateProductRequest request) {
        ProductSpu spu = productSpuMapper.selectOne(Wrappers.<ProductSpu>lambdaQuery()
                .eq(ProductSpu::getId, spuId)
                .eq(ProductSpu::getMerchantId, merchantId));
        if (spu == null) {
            return Optional.empty();
        }
        long categoryId = Long.parseLong(request.categoryId());
        String descriptionHtml = org.jsoup.Jsoup.clean(
                request.descriptionHtml(), org.jsoup.safety.Safelist.relaxed());

        int changed = productSpuMapper.update(null, Wrappers.<ProductSpu>lambdaUpdate()
                .eq(ProductSpu::getId, spuId)
                .eq(ProductSpu::getMerchantId, merchantId)
                .eq(ProductSpu::getVersion, request.version())
                .set(ProductSpu::getCategoryId, categoryId)
                .set(ProductSpu::getTitle, request.title())
                .set(ProductSpu::getSubtitle, request.subtitle())
                .set(ProductSpu::getBrand, request.brand())
                .set(ProductSpu::getDescriptionHtml, descriptionHtml)
                .setSql("version = version + 1"));

        if (changed == 0) {
            throw new StatusConflict("PRODUCT_VERSION_CONFLICT", "商品已更新，请刷新后重试");
        }
        Map<Long, ProductSku> existingSkus = new HashMap<>();
        for (ProductSku sku : productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getSpuId, spuId))) {
            existingSkus.put(sku.getId(), sku);
        }

        Set<Long> submittedIds = new HashSet<>();
        for (UpdateProductRequest.SkuUpdate input : request.skus()) {
            if (input.skuId() != null) {
                long skuId = Long.parseLong(input.skuId());
                if (!existingSkus.containsKey(skuId) || !submittedIds.add(skuId)) {
                    throw new IllegalArgumentException("SKU ID 重复或不属于当前商品");
                }
            }
        }

        for (UpdateProductRequest.SkuUpdate input : request.skus()) {
            saveUpdatedSku(spuId, input);
        }

        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getSpuId, spuId));
        for (CreateProductRequest.ImageRequest input : request.images()) {
            ProductImage image = new ProductImage();
            image.setSpuId(spuId);
            image.setUrl(input.url());
            image.setAltText(input.altText() == null ? "" : input.altText());
            image.setSortOrder(input.sortOrder() == null ? 0 : input.sortOrder());
            image.setPrimaryImage(Boolean.TRUE.equals(input.isPrimary()));
            productImageMapper.insert(image);
        }

        var detail = getProductDetail(merchantId, spuId).orElseThrow(
                () -> new StatusConflict("PRODUCT_HAS_NO_SKU", "商品缺少 SKU，无法返回商品详情"));
        publishProductChangedAfterCommit(spuId, request.version() + 1);
        return Optional.of(detail);
    }

    private void saveUpdatedSku(long spuId, UpdateProductRequest.SkuUpdate input) {
        String specJson;
        try {
            specJson = objectMapper.writeValueAsString(new TreeMap<>(input.specs()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 SKU 规格", e);
        }
        byte[] specHash = hashSpecs(specJson);

        if (input.skuId() == null) {
            ProductSku sku = new ProductSku();
            sku.setSpuId(spuId);
            sku.setSkuCode(input.skuCode());
            sku.setSpecJson(specJson);
            sku.setSpecHash(specHash);
            sku.setPrice(input.price());
            sku.setOriginalPrice(input.originalPrice());
            sku.setEnabled(input.enabled());
            productSkuMapper.insert(sku);
            return;
        }

        productSkuMapper.update(null, Wrappers.<ProductSku>lambdaUpdate()
                .eq(ProductSku::getId, Long.parseLong(input.skuId()))
                .eq(ProductSku::getSpuId, spuId)
                .set(ProductSku::getSkuCode, input.skuCode())
                .set(ProductSku::getSpecJson, specJson)
                .set(ProductSku::getSpecHash, specHash)
                .set(ProductSku::getPrice, input.price())
                .set(ProductSku::getOriginalPrice, input.originalPrice())
                .set(ProductSku::getEnabled, input.enabled()));
    }
}

