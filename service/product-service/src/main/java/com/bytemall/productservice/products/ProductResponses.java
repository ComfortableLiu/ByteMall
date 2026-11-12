package com.bytemall.productservice.products;

import java.util.List;
import java.util.Map;

public class ProductResponses {
    private ProductResponses() {
    }

    public record ProductSummary(
            String spuId,
            String merchantId,
            String title,
            String subtitle,
            String brand,
            String categoryId,
            String coverImage,
            long minPrice,
            long maxPrice,
            long salesCount,
            String status,
            String publishedAt
    ) {
    }

    public record ProductPage(
            int page,
            int size,
            long total,
            List<ProductSummary> items
    ) {
    }


    public record Image(String url, String altText, int sortOrder, boolean isPrimary) {}

    public record Sku(
            String skuId,
            String skuCode,
            Map<String, String> specs,
            long price,
            Long originalPrice,
            int availableStock,
            boolean enabled
    ) {}

    public record ProductDetail(
            String spuId,
            String merchantId,
            String title,
            String subtitle,
            String brand,
            String categoryId,
            String coverImage,
            long minPrice,
            long maxPrice,
            long salesCount,
            String status,
            String publishedAt,
            String descriptionHtml,
            List<Image> images,
            List<Sku> skus,
            long version
    ) {}

}
