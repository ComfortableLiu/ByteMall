package com.bytemall.productservice.merchant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductStatusRequest(
        @NotNull TargetStatus status,
        @NotNull @PositiveOrZero Long version
) {

    public enum TargetStatus {
        ON_SALE,
        OFF_SALE
    }
}
