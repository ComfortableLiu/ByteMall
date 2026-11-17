package com.bytemall.cartservice.cart;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CartResponses {

    private CartResponses() {
    }

    public record Cart(
            List<Item> items,
            long selectedAmount,
            int selectedQuantity
    ) {
    }

    // 商品信息是读取购物车时补齐的快照，金额单位为人民币分。
    public record Item(
            String userId,
            String skuId,
            int quantity,
            boolean selected,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant addedAt,
            String spuId,
            String merchantId,
            String productTitle,
            String productImage,
            String productStatus,
            Map<String, String> specs,
            long unitPrice,
            int availableStock,
            boolean enabled
    ) {
    }
}
