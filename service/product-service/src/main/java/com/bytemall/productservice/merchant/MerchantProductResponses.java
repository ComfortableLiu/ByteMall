package com.bytemall.productservice.merchant;

public final class MerchantProductResponses {

    private MerchantProductResponses() {
    }

    public record CreatedProduct(String spuId) {
    }
}
