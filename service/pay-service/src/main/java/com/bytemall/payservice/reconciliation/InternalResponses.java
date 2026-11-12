package com.bytemall.payservice.reconciliation;

import com.bytemall.payservice.payments.PaymentStatus;

public final class InternalResponses {

    private InternalResponses() {
    }

    public record MockPayments(
            String notifyId,
            String orderNo,
            long amount,
            PaymentStatus status,
            String signature
    ) {
    }
}
