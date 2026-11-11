package com.bytemall.payservice.reconciliation;

import java.time.LocalDate;

public final class ReconciliationResponses {

    private ReconciliationResponses() {
    }

    public enum ReconciliationStatus {
        MATCHED,
        DIFFERENT,
        MISSING_LOCAL,
        MISSING_CHANNEL
    }

    public record ReconciliationRecord(
            String channel,
            String channelTradeNo,
            String payNo,
            LocalDate bizDate,
            long channelAmount,
            long localAmount,
            ReconciliationStatus status
    ) {
    }
}
