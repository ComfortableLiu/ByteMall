package com.bytemall.productservice;

import com.bytemall.productservice.merchant.CreateProductRequest;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductSpuMapper;
import com.bytemall.productservice.merchant.MerchantProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "product.image.allowed-host=byte-mall.oss-cn-beijing.aliyuncs.com")
class MerchantProductServiceTest {

    @Autowired
    private MerchantProductService merchantProductService;

    @Autowired
    private ProductSpuMapper productSpuMapper;

    @Test
    @Transactional
    void createDraftSavesSpuAndReturnsId() {
        Long merchantId = 1L;
        Long categoryId = 1L; // 改成你查到的分类 ID

        var sku = new CreateProductRequest.SkuRequest(
                "TEST-HEADPHONE-BLACK",
                Map.of("颜色", "黑色"),
                19900L,
                24900L,
                true
        );
        var images = new CreateProductRequest.ImageRequest(
                "https://byte-mall.oss-cn-beijing.aliyuncs.com/products/spu-00002-primary.png",
                "黑色无线耳机",
                0,
                true
        );
        var request = new CreateProductRequest(
                "测试无线耳机",
                "用于验证草稿创建",
                categoryId.toString(),
                "ByteMall",
                null,
                List.of(images),
                List.of(sku)
        );


        Long spuId = merchantProductService.createDraft(merchantId, request);
        assertNotNull(spuId, "插入后应返回数据库生成的商品 ID");

        ProductSpu saved = productSpuMapper.selectById(spuId);
        assertNotNull(saved, "应能用商品 ID 查到刚插入的记录");
        assertEquals(request.title(), saved.getTitle());
        assertEquals(merchantId, saved.getMerchantId());
        assertEquals(categoryId, saved.getCategoryId());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(Long.valueOf(0L), saved.getVersion());
    }
}