package com.bytemall.productservice.products;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
            @JsonInclude(JsonInclude.Include.NON_NULL) String publishedAt
    ) {
    }

    public record ProductPage(
            int page,
            int size,
            long total,
            List<ProductSummary> items
    ) {
    }
}
