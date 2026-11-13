package com.bytemall.productservice.products;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.common.ProductDetailReader;
import com.bytemall.productservice.common.ProductListReader;
import com.bytemall.productservice.entity.ProductSpu;
import com.bytemall.productservice.mapper.ProductImageMapper;
import com.bytemall.productservice.mapper.ProductSkuMapper;
import com.bytemall.productservice.mapper.ProductSpuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductDetailReader productDetailReader;


    private final ObjectMapper objectMapper;
    private final ProductStockClient productStockClient;
    private final ProductListReader productListReader;

    // 分页获取商品列表
    public ProductResponses.ProductPage getProductsList(int page, int size) {
        // 计算偏移量
        long offset = (page - 1L) * size;
        long total = productSpuMapper.countOnSale();
        // 获取在售spu列表
        List<ProductSpu> spuList = productSpuMapper.selectOnSalePage(size, offset);

        return new ProductResponses.ProductPage(page, size, total, productListReader.read(spuList, ProductListReader.SkuScope.ENABLED_ONLY));
    }


    public Optional<ProductResponses.ProductDetail> getProductDetail(long spuId) {
        ProductSpu spu = productSpuMapper.selectOne(
                Wrappers.<ProductSpu>lambdaQuery()
                        .eq(ProductSpu::getId, spuId)
                        .eq(ProductSpu::getStatus, "ON_SALE"));
        if (spu == null) {
            return Optional.empty();
        }


        return productDetailReader.read(spu, ProductDetailReader.SkuScope.ENABLED_ONLY);
    }

    ;


}

