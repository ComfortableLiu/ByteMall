package com.bytemall.payservice.payments;

import java.time.Instant;

public final class PaymentResponses {

    private PaymentResponses() {
    }

    public record Payment(
            String payNo,
            String orderNo,
            String userId,
            PaymentChannel channel,
            long amount,
            PaymentStatus status,
            String cashierToken,
            Instant expireAt,
            Instant paidAt,
            Instant createdAt
    ) {
    }
}
