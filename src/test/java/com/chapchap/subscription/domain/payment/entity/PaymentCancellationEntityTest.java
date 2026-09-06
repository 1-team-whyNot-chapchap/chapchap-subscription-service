package com.chapchap.subscription.domain.payment.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentCancellationEntityTest {
    @Test
    void 원결제와_배분에_성공취소금액을_같이_반영한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        PaymentTransaction original = PaymentTransaction.createFirstSubscriptionPayment(
            1L, 2L, 3L, 10_000L, now, LocalDate.of(2026, 9, 8),
            LocalDate.of(2026, 10, 5), "payment-key-123456", now
        );
        original.markAsSucceeded();
        PaymentAllocation allocation = PaymentAllocation.create(
            4L, 5L, PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 10_000L
        );

        original.applySuccessfulCancellation(4_000L);
        allocation.cancel(4_000L);

        assertThat(original.getCumulativeCancelAmount()).isEqualTo(4_000L);
        assertThat(original.getCancelableAmount()).isEqualTo(6_000L);
        assertThat(allocation.currentCancelableAmount()).isEqualTo(6_000L);
    }

    @Test
    void 실패한_취소거래는_같은_거래로_재시도한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        PaymentTransaction cancellation = PaymentTransaction.createCancellation(
            1L, 2L, 3L, 4L, 5L, PaymentTransactionType.CANCELLATION_BEFORE_START,
            10_000L, now, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 5),
            "cancel-key-123456", now
        );
        cancellation.markCancellationFailed();
        cancellation.retryCancellation("retry-key-1234567");

        assertThat(cancellation.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        assertThat(cancellation.getExternalRequestIdempotencyKey()).isEqualTo("retry-key-1234567");
        assertThat(cancellation.getPaymentStateVersion()).isEqualTo(2L);
    }

    @Test
    void 원결제취소_처리시도에는_현재결제수단을_저장하지_않는다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        PaymentAttempt attempt = PaymentAttempt.cancellationSuccess(
            1L, PaymentProviderCode.PORTONE, 1, "cancel-key-123456", 10_000L,
            now, now.plusSeconds(1), "PAY-original", "cancel-1", "SUCCEEDED"
        );

        assertThat(attempt.getPaymentMethodId()).isNull();
        assertThat(attempt.getExternalPaymentId()).isEqualTo("PAY-original");
    }
}
