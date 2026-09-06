package com.chapchap.subscription.global.kafka.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SubscriptionCancellationConfirmedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "SUBSCRIPTION_CANCELLATION_CONFIRMED";

    public record Data(
        String cancellationType,
        OffsetDateTime requestedAt,
        OffsetDateTime effectiveAt,
        LocalDate lastUseDate,
        String refundResult
    ) {
    }
}
