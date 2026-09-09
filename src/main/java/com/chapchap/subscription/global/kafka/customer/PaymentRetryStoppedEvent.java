package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

public record PaymentRetryStoppedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "PAYMENT_RETRY_STOPPED";

    public record Data(
        String paymentId,
        long paymentVersion,
        String paymentType,
        String paymentStatus,
        OffsetDateTime stoppedAt
    ) {
    }
}
