package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryRefundCancellationCompletionServiceTest {
    @Test
    void 성공_취소액을_대상_주문_배분과_원결제와_환불에_함께_반영한다() {
        Fixture fixture = fixture(3_000L, 3_000L);
        PaymentCancellationExecutionResult result = fixture.successResult();

        RefundStatus status = fixture.service.complete(result);

        assertThat(status).isEqualTo(RefundStatus.COMPLETED);
        assertThat(fixture.allocation.currentCancelableAmount()).isEqualTo(7_000L);
        assertThat(fixture.original.getCancelableAmount()).isEqualTo(7_000L);
        assertThat(fixture.refund.getSuccessfulRefundAmount()).isEqualTo(3_000L);
    }

    @Test
    void 일부_성공한_환불의_다음_취소가_실패하면_확인필요로_보존한다() {
        Fixture fixture = fixture(4_000L, 3_000L);
        fixture.refund.addSuccessfulAmount(1_000L, LocalDateTime.of(2026, 9, 6, 17, 59));
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 18, 0);
        var failed = new PaymentCancellationExecutionResult(
            300L, PaymentProviderCode.PORTONE, "cancel-key", 3_000L, now, now.plusSeconds(1),
            PaymentCancellationResult.declined("external-pay", "DECLINED"));

        assertThat(fixture.service.complete(failed)).isEqualTo(RefundStatus.REVIEW_REQUIRED);
        assertThat(fixture.allocation.currentCancelableAmount()).isEqualTo(10_000L);
        assertThat(fixture.original.getCancelableAmount()).isEqualTo(10_000L);
    }

    private Fixture fixture(long refundAmount, long cancellationAmount) {
        var payments = mock(PaymentTransactionRepository.class);
        var attempts = mock(PaymentAttemptRepository.class);
        var allocations = mock(PaymentAllocationRepository.class);
        var refunds = mock(RefundRepository.class);
        var service = new DeliveryRefundCancellationCompletionService(payments, attempts, allocations, refunds,
            mock(com.chapchap.subscription.global.kafka.customer.CustomerRefundEventPublisher.class));
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 18, 0);
        PaymentTransaction original = PaymentTransaction.createFirstSubscriptionPayment(
            10L, 1L, 2L, 10_000L, now, LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 28), "pay-key", now);
        ReflectionTestUtils.setField(original, "id", 100L);
        original.markAsSucceeded();
        Refund refund = Refund.createDeliveryPartialCancellation(
            1L, 400L, "11111111-1111-4111-8111-111111111111", refundAmount);
        ReflectionTestUtils.setField(refund, "id", 200L);
        PaymentTransaction cancellation = PaymentTransaction.createDeliveryCancellation(
            10L, 1L, 2L, 200L, 100L, cancellationAmount, now,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28), "cancel-key", now);
        ReflectionTestUtils.setField(cancellation, "id", 300L);
        PaymentAllocation allocation = PaymentAllocation.create(
            400L, 100L, PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 10_000L);
        when(payments.findWithLockById(300L)).thenReturn(Optional.of(cancellation));
        when(payments.findWithLockById(100L)).thenReturn(Optional.of(original));
        when(refunds.findWithLockById(200L)).thenReturn(Optional.of(refund));
        when(attempts.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(300L)).thenReturn(List.of());
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allocations.findWithLockAllByOrderIdAndOriginalPaymentTransactionId(400L, 100L))
            .thenReturn(List.of(allocation));
        return new Fixture(service, original, refund, allocation);
    }

    private record Fixture(
        DeliveryRefundCancellationCompletionService service,
        PaymentTransaction original,
        Refund refund,
        PaymentAllocation allocation
    ) {
        private PaymentCancellationExecutionResult successResult() {
            LocalDateTime now = LocalDateTime.of(2026, 9, 6, 18, 0);
            return new PaymentCancellationExecutionResult(
                300L, PaymentProviderCode.PORTONE, "cancel-key", 3_000L, now, now.plusSeconds(1),
                PaymentCancellationResult.succeeded("external-pay", "external-cancel"));
        }
    }
}
