package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationPreparation;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationType;
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
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class PeriodRefundPreparationServiceTest {
    @Test
    void 전체환불금액을_확정하고_추가결제부터_취소거래_하나만_준비한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        RefundRepository refunds = mock(RefundRepository.class);
        PeriodRefundPreparationService service = new PeriodRefundPreparationService(
            subscriptions, periods, orders, allocations, payments, refunds
        );
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        Subscription subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.markScheduled();
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, 1, LocalDate.of(2026, 9, 8), now
        );
        ReflectionTestUtils.setField(period, "id", 2L);
        period.markScheduled();
        PaymentTransaction first = original(100L, PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT, 6_000L, now.plusMinutes(1));
        PaymentTransaction change = original(101L, PaymentTransactionType.SETTING_CHANGE_PAYMENT, 4_000L, now);
        PaymentAllocation firstAllocation = PaymentAllocation.create(20L, 100L, PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 6_000L);
        PaymentAllocation changeAllocation = PaymentAllocation.create(20L, 101L, PaymentAllocationType.SETTING_CHANGE_PAYMENT, 4_000L);
        when(subscriptions.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periods.findWithLockById(2L)).thenReturn(Optional.of(period));
        when(periods.findById(2L)).thenReturn(Optional.of(period));
        when(refunds.findBySubscriptionPeriodId(2L)).thenReturn(Optional.empty());
        when(allocations.findAllByOrderIdInOrderByIdAsc(List.of(20L)))
            .thenReturn(List.of(firstAllocation, changeAllocation));
        when(payments.findAllById(any())).thenReturn(List.of(first, change));
        when(refunds.saveAndFlush(any())).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", 300L);
            return refund;
        });
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 400L);
            return transaction;
        });
        SubscriptionCancellationPreparation prepared = new SubscriptionCancellationPreparation(
            SubscriptionCancellationType.CANCELLATION_BEFORE_START, 1L, 2L, List.of(20L), now
        );

        PreparedPeriodRefund result = service.prepare(prepared);

        assertThat(result.refundId()).isEqualTo(300L);
        assertThat(result.cancellationTransactionIds()).containsExactly(400L);
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refunds).saveAndFlush(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getRefundAmount()).isEqualTo(10_000L);
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(payments).saveAndFlush(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getOriginalPaymentTransactionId()).isEqualTo(101L);
    }

    private PaymentTransaction original(Long id, PaymentTransactionType type, long amount, LocalDateTime occurredAt) {
        PaymentTransaction transaction = PaymentTransaction.createFirstSubscriptionPayment(
            10L, 1L, 2L, amount, occurredAt, LocalDate.of(2026, 9, 8),
            LocalDate.of(2026, 10, 5), "payment-key-" + id, occurredAt
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        ReflectionTestUtils.setField(transaction, "transactionType", type);
        transaction.markAsSucceeded();
        return transaction;
    }
}
