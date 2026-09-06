package com.chapchap.subscription.domain.payment.entity;

import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTransactionTest {
    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 9, 3, 21, 0);
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 7);
    private static final LocalDate END_DATE = LocalDate.of(2026, 10, 4);

    @Test
    void firstPaymentTransactionStartsInProcessingState() {
        PaymentTransaction transaction = createTransaction();

        assertTrue(PublicIdFormat.isUuidV4(transaction.getPublicId()));
        assertEquals(PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT, transaction.getTransactionType());
        assertEquals(PaymentTransactionStatus.PROCESSING, transaction.getStatus());
        assertEquals("PAYMENT:FIRST:30", transaction.getBusinessDeduplicationKey());
        assertEquals("request-key-1", transaction.getExternalRequestIdempotencyKey());
        assertEquals(0L, transaction.getPaymentStateVersion());
        assertNull(transaction.getOriginalPaymentAmount());
    }

    @Test
    void successfulFirstPaymentInitializesCancelableAmountsAndClearsRequestKey() {
        PaymentTransaction transaction = createTransaction();

        transaction.markAsSucceeded();

        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(100_000L, transaction.getOriginalPaymentAmount());
        assertEquals(0L, transaction.getCumulativeCancelAmount());
        assertEquals(100_000L, transaction.getCancelableAmount());
        assertNull(transaction.getExternalRequestIdempotencyKey());
        assertEquals(1L, transaction.getPaymentStateVersion());
    }

    @Test
    void failedFirstPaymentClearsRequestKeyWithoutInitializingOriginalPaymentAmounts() {
        PaymentTransaction transaction = createTransaction();

        transaction.markAsFailed();

        assertEquals(PaymentTransactionStatus.FAILED, transaction.getStatus());
        assertNull(transaction.getOriginalPaymentAmount());
        assertNull(transaction.getCumulativeCancelAmount());
        assertNull(transaction.getCancelableAmount());
        assertNull(transaction.getExternalRequestIdempotencyKey());
        assertEquals(1L, transaction.getPaymentStateVersion());
    }

    @Test
    void completedTransactionCannotBeCompletedAgain() {
        PaymentTransaction transaction = createTransaction();
        transaction.markAsSucceeded();

        assertThrows(IllegalStateException.class, transaction::markAsFailed);
    }

    @Test
    void regularPaymentUsesPeriodBusinessKeyAndAllowsOneRetry() {
        PaymentTransaction transaction = PaymentTransaction.createRegularPayment(
            10L, 20L, 31L, 100_000L, REFERENCE_AT, START_DATE, END_DATE,
            "regular-request-1", REFERENCE_AT
        );

        assertEquals(PaymentTransactionType.REGULAR_PAYMENT, transaction.getTransactionType());
        assertEquals("PAYMENT:REGULAR:31", transaction.getBusinessDeduplicationKey());

        transaction.waitForRegularPaymentRetry();
        assertEquals(PaymentTransactionStatus.RETRY_WAITING, transaction.getStatus());
        assertNull(transaction.getExternalRequestIdempotencyKey());

        transaction.startRegularPaymentRetry("regular-request-2");
        assertEquals(PaymentTransactionStatus.PROCESSING, transaction.getStatus());
        assertEquals("regular-request-2", transaction.getExternalRequestIdempotencyKey());
        assertEquals(2L, transaction.getPaymentStateVersion());
    }

    @Test
    void firstPaymentCannotUseRegularPaymentRetryTransition() {
        PaymentTransaction transaction = createTransaction();

        assertThrows(IllegalStateException.class, transaction::waitForRegularPaymentRetry);
    }

    @Test
    void invalidPeriodRangeIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PaymentTransaction.createFirstSubscriptionPayment(
                10L,
                20L,
                30L,
                100_000L,
                REFERENCE_AT,
                END_DATE,
                START_DATE,
                "request-key-1",
                REFERENCE_AT
            )
        );
    }

    private PaymentTransaction createTransaction() {
        return PaymentTransaction.createFirstSubscriptionPayment(
            10L,
            20L,
            30L,
            100_000L,
            REFERENCE_AT,
            START_DATE,
            END_DATE,
            "request-key-1",
            REFERENCE_AT
        );
    }
}
