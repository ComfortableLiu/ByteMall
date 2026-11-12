package com.bytemall.payservice.merchant;

import com.bytemall.payservice.payments.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class MerchantRequests {

    private MerchantRequests() {
    }

    public record ReconciliationsQuery(
            @NotBlank String orderNo,
            @NotBlank PaymentStatus status,
            @NotNull Integer amount
    ) {
    }
}
