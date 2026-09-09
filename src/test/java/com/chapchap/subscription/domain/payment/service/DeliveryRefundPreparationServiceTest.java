package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryRefundPreparationServiceTest {
    @Test
    void 주문_저장금액으로_환불을_만들고_최근_설정변경_결제부터_취소한다() {
        var orders = mock(OrderRepository.class);
        var refunds = mock(RefundRepository.class);
        var allocations = mock(PaymentAllocationRepository.class);
        var payments = mock(PaymentTransactionRepository.class);
        var time = mock(KstReferenceTimeProvider.class);
        var service = new DeliveryRefundPreparationService(orders, refunds, allocations, payments, time);
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 18, 0);
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(20L);
        when(order.getUserId()).thenReturn(10L);
        when(order.getSubscriptionId()).thenReturn(30L);
        when(order.getActualAllocatedAmount()).thenReturn(8_000L);
        when(orders.findWithLockByPublicId("550e8400-e29b-41d4-a716-446655440000"))
            .thenReturn(Optional.of(order));
        when(refunds.findByExternalDeliveryId("delivery-1")).thenReturn(Optional.empty());
        when(refunds.findByOrderId(20L)).thenReturn(Optional.empty());
        when(refunds.saveAndFlush(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 40L);
            return value;
        });

        PaymentTransaction first = original(100L, false, now.minusDays(10));
        PaymentTransaction change = original(101L, true, now.minusDays(1));
        PaymentAllocation firstAllocation = PaymentAllocation.create(
            20L, 100L, PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 5_000L);
        PaymentAllocation changeAllocation = PaymentAllocation.create(
            20L, 101L, PaymentAllocationType.SETTING_CHANGE_PAYMENT, 3_000L);
        when(allocations.findAllByOrderIdOrderByIdAsc(20L))
            .thenReturn(List.of(firstAllocation, changeAllocation));
        when(payments.findAllById(any())).thenReturn(List.of(first, change));
        when(payments.findAllByRefundIdOrderByOccurredAtAscIdAsc(40L)).thenReturn(List.of());
        when(payments.findWithLockById(101L)).thenReturn(Optional.of(change));
        when(payments.findAllByOriginalPaymentTransactionIdAndStatus(101L, PaymentTransactionStatus.PROCESSING))
            .thenReturn(List.of());
        when(time.now()).thenReturn(now);
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentTransaction value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 50L);
            return value;
        });

        PreparedDeliveryRefund result = service.start(
            new DeliveryRefundCommand("delivery-1", "550e8400-e29b-41d4-a716-446655440000", 10L));

        assertThat(result.cancellationTransactionId()).isEqualTo(50L);
        ArgumentCaptor<PaymentTransaction> saved = ArgumentCaptor.forClass(PaymentTransaction.class);
        org.mockito.Mockito.verify(payments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTransactionType())
            .isEqualTo(PaymentTransactionType.DELIVERY_PARTIAL_CANCELLATION);
        assertThat(saved.getValue().getOriginalPaymentTransactionId()).isEqualTo(101L);
        assertThat(saved.getValue().getTransactionAmount()).isEqualTo(3_000L);
    }

    private PaymentTransaction original(Long id, boolean settingChange, LocalDateTime occurredAt) {
        PaymentTransaction payment = settingChange
            ? PaymentTransaction.createSettingChangePayment(
                10L, 30L, 3L, 4L, 3_000L, occurredAt,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 2), "key-" + id, occurredAt)
            : PaymentTransaction.createFirstSubscriptionPayment(
                10L, 30L, 3L, 5_000L, occurredAt,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
                "key-" + id, occurredAt);
        ReflectionTestUtils.setField(payment, "id", id);
        payment.markAsSucceeded();
        return payment;
    }
}
