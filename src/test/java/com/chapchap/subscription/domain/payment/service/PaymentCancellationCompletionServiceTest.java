package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCancellationCompletionServiceTest {
    PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
    RefundRepository refunds = mock(RefundRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    PaymentCancellationCompletionService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCancellationCompletionService(payments, attempts, allocations, refunds, orders);
    }

    @Test
    void 성공응답을_원결제와_배분과_환불에_원자적으로_반영한다() {
        Fixture fixture = fixture();
        PaymentCancellationExecutionResult result = result(
            PaymentCancellationResult.succeeded("550e8400-e29b-41d4-a716-446655440000", "cancel-1")
        );

        RefundStatus status = service.complete(result);

        assertThat(status).isEqualTo(RefundStatus.COMPLETED);
        assertThat(fixture.cancellation().getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(fixture.original().getCancelableAmount()).isZero();
        assertThat(fixture.allocation().currentCancelableAmount()).isZero();
        assertThat(fixture.refund().getSuccessfulRefundAmount()).isEqualTo(10_000L);
        verify(attempts).save(any());
    }

    @Test
    void 첫_명시실패는_금액집계를_바꾸지_않고_환불실패로_남긴다() {
        Fixture fixture = fixture();
        PaymentCancellationExecutionResult result = result(
            PaymentCancellationResult.declined("550e8400-e29b-41d4-a716-446655440000", "DECLINED")
        );

        RefundStatus status = service.complete(result);

        assertThat(status).isEqualTo(RefundStatus.FAILED);
        assertThat(fixture.cancellation().getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(fixture.original().getCancelableAmount()).isEqualTo(10_000L);
        assertThat(fixture.allocation().currentCancelableAmount()).isEqualTo(10_000L);
    }

    private Fixture fixture() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        PaymentTransaction original = PaymentTransaction.createFirstSubscriptionPayment(
            10L, 1L, 2L, 10_000L, now, LocalDate.of(2026, 9, 8),
            LocalDate.of(2026, 10, 5), "payment-key-123456", now
        );
        ReflectionTestUtils.setField(original, "id", 100L);
        original.markAsSucceeded();
        Refund refund = Refund.createPeriodCancellation(
            1L, 2L, RefundType.CANCELLATION_BEFORE_START, 10_000L
        );
        ReflectionTestUtils.setField(refund, "id", 300L);
        PaymentTransaction cancellation = PaymentTransaction.createCancellation(
            10L, 1L, 2L, 300L, 100L, PaymentTransactionType.CANCELLATION_BEFORE_START,
            10_000L, now, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 5),
            "cancel-key-123456", now
        );
        ReflectionTestUtils.setField(cancellation, "id", 200L);
        PaymentAllocation allocation = PaymentAllocation.create(
            400L, 100L, PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 10_000L
        );
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(400L);
        when(payments.findById(200L)).thenReturn(Optional.of(cancellation));
        when(payments.findById(100L)).thenReturn(Optional.of(original));
        when(refunds.findById(300L)).thenReturn(Optional.of(refund));
        when(attempts.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(200L)).thenReturn(List.of());
        when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order));
        when(allocations.findAllByOriginalPaymentTransactionIdOrderByIdAsc(100L))
            .thenReturn(List.of(allocation));
        return new Fixture(original, cancellation, allocation, refund);
    }

    private PaymentCancellationExecutionResult result(PaymentCancellationResult providerResult) {
        return new PaymentCancellationExecutionResult(
            200L, PaymentProviderCode.PORTONE, "cancel-key-123456", 10_000L,
            LocalDateTime.of(2026, 9, 6, 12, 0), LocalDateTime.of(2026, 9, 6, 12, 0, 1),
            providerResult
        );
    }

    private record Fixture(PaymentTransaction original, PaymentTransaction cancellation,
                           PaymentAllocation allocation, Refund refund) {
    }
}
