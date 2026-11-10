package com.bytemall.searchservice.product;

import java.time.Instant;

public record ProductChangedEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long version,
        Instant occurredAt
) {
}
