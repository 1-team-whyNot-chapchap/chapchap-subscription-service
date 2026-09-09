package com.chapchap.subscription.domain.payment.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAttemptTest {
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 3, 21, 0);
    private static final LocalDateTime RESPONDED_AT = REQUESTED_AT.plusSeconds(1);

    @Test
    void successfulAttemptKeepsProviderIdentifiers() {
        PaymentAttempt attempt = PaymentAttempt.success(
            1L,
            2L,
            PaymentProviderCode.PORTONE,
            1,
            "request-key-1",
            100_000L,
            REQUESTED_AT,
            RESPONDED_AT,
            "external-payment-1",
            "external-transaction-1",
            "PAID"
        );

        assertEquals(PaymentAttemptResult.SUCCESS, attempt.getResult());
        assertEquals("external-payment-1", attempt.getExternalPaymentId());
        assertEquals("external-transaction-1", attempt.getExternalTransactionRef());
        assertNull(attempt.getFailureReason());
    }

    @Test
    void failedAttemptKeepsFailureInformationWithoutTransactionReference() {
        PaymentAttempt attempt = PaymentAttempt.failure(
            1L,
            2L,
            PaymentProviderCode.PORTONE,
            1,
            "request-key-1",
            100_000L,
            REQUESTED_AT,
            RESPONDED_AT,
            "external-payment-1",
            "PAYMENT_FAILED",
            "카드 승인이 거절되었습니다."
        );

        assertEquals(PaymentAttemptResult.FAILURE, attempt.getResult());
        assertEquals("카드 승인이 거절되었습니다.", attempt.getFailureReason());
        assertNull(attempt.getExternalTransactionRef());
    }

    @Test
    void responseBeforeRequestIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PaymentAttempt.failure(
                1L,
                2L,
                PaymentProviderCode.PORTONE,
                1,
                "request-key-1",
                100_000L,
                REQUESTED_AT,
                REQUESTED_AT.minusSeconds(1),
                "external-payment-1",
                "PAYMENT_FAILED",
                "카드 승인이 거절되었습니다."
            )
        );
    }

    @Test
    void blankFailureReasonIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PaymentAttempt.failure(
                1L,
                2L,
                PaymentProviderCode.PORTONE,
                1,
                "request-key-1",
                100_000L,
                REQUESTED_AT,
                RESPONDED_AT,
                "external-payment-1",
                "PAYMENT_FAILED",
                " "
            )
        );
    }
}
