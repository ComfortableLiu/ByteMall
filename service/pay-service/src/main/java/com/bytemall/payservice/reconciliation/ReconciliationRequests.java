package com.bytemall.payservice.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class ReconciliationRequests {

    private ReconciliationRequests() {
    }

    public record CreateJob(
            @NotBlank String channel,
            @NotNull LocalDate bizDate
    ) {
    }
}
