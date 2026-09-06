package com.chapchap.subscription.global.kafka.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentFailedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Object data
) {
    public static final String EVENT_TYPE = "PAYMENT_FAILED";

    public record RetryWaitingData(
        String paymentId,
        long paymentVersion,
        String paymentType,
        String paymentStatus,
        long requestedAmount,
        OffsetDateTime failedAt,
        OffsetDateTime retryScheduledAt
    ) {
    }

    public record FinalFailureData(
        String paymentId,
        long paymentVersion,
        String paymentType,
        String paymentStatus,
        long requestedAmount,
        OffsetDateTime failedAt,
        LocalDate currentPeriodEndDate,
        String failureCode
    ) {
    }

    public record SettingChangeFailureData(
        String paymentId,
        long paymentVersion,
        String paymentType,
        String paymentStatus,
        long requestedAmount,
        OffsetDateTime failedAt,
        LocalDate settingEffectiveDate,
        String failureCode
    ) {
    }
}
