package com.bytemall.payservice.merchant;

import com.bytemall.payservice.payments.PaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MerchantResponses {

    private MerchantResponses() {
    }

    public record Reconciliations(
            String channel,
            String channelTradeNo,
            String payNo,
            LocalDate bizDate,
            int channelAmount,
            int localAmount,
            PaymentStatus status
    ) {
    }
}
