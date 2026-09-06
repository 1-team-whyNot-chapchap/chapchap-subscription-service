package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

public record RefundFailedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "REFUND_FAILED";

    public record Data(
        String refundId,
        String refundType,
        long requestedAmount,
        String currency,
        OffsetDateTime failedAt,
        String failureCode
    ) {
    }
}
