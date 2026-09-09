package com.chapchap.subscription.global.kafka.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentCompletedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "PAYMENT_COMPLETED";

    public record Data(
        String paymentId,
        long paymentVersion,
        String paymentType,
        String paymentStatus,
        long paidAmount,
        long discountAmount,
        String currency,
        LocalDate periodStartDate,
        LocalDate periodEndDate
    ) {
    }
}
