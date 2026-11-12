package com.bytemall.productservice.merchant;

public final class ProductResponses {

    private ProductResponses() {
    }

    public record CreatedProduct(String spuId) {
    }
}
