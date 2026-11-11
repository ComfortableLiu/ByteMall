package com.bytemall.payservice.callbacks;

public final class PaymentCallbackRequests {

    private PaymentCallbackRequests() {
    }

    public record PaymentNotification(
            String notifyId,
            String orderNo,
            Long amount,
            String status,
            String signature
    ) {
    }
}
