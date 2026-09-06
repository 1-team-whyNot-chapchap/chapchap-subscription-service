package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

public record SubscriptionEndedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "SUBSCRIPTION_ENDED";

    public record Data(OffsetDateTime endedAt, String endReason) {
    }
}
