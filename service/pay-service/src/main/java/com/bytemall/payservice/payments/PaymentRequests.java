package com.bytemall.payservice.payments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PaymentRequests {

    private PaymentRequests() {
    }

    public record CreatePayment(
            @NotBlank String orderNo,
            @NotNull PaymentChannel channel
    ) {
    }
}
