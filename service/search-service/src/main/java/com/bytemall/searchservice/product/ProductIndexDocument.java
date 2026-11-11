package com.bytemall.searchservice.product;

public record ProductIndexDocument(
        String spuId,
        String merchantId,
        String categoryId,
        String brand,
        String coverImage,
        String title,
        String subtitle,
        String descriptionText,
        Long minPrice,
        Long maxPrice,
        long salesCount,
        String status,
        String publishedAt,
        String updatedAt,
        long version
) {
}
