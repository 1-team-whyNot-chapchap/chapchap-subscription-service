package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

public record RefundCompletedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "REFUND_COMPLETED";

    public record Data(
        String refundId,
        String refundType,
        long refundedAmount,
        String currency,
        OffsetDateTime completedAt
    ) {
    }
}
