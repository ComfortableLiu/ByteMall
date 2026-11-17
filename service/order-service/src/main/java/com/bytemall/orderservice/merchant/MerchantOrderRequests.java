package com.bytemall.orderservice.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class MerchantOrderRequests {

    private MerchantOrderRequests() {
    }

    public record Shipment(
            @NotBlank @Size(max = 60) String carrier,
            @NotBlank @Size(max = 64) String trackingNo,
            @PositiveOrZero Long version
    ) {
    }
}
