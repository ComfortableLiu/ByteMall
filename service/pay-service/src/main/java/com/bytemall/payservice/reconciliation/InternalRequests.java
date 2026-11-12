package com.bytemall.payservice.reconciliation;

import com.bytemall.payservice.payments.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class InternalRequests {

    private InternalRequests() {
    }

    public record MockPaymentsQuery(
            @NotBlank String orderNo,
            @NotBlank PaymentStatus status,
            @NotNull Integer amount
    ) {
    }
}
