package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
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
import com.chapchap.subscription.domain.subscription.service.SettingChangeAmountService;
import com.chapchap.subscription.domain.subscription.service.SettingChangeAmountSnapshot;
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

class SettingChangeCancellationCompletionServiceTest {
    @Test
    void 감액_성공금액만_기존대상주문의_배분과_원결제에_반영한다() {
        var payments = mock(PaymentTransactionRepository.class);
        var attempts = mock(PaymentAttemptRepository.class);
        var allocations = mock(PaymentAllocationRepository.class);
        var refunds = mock(RefundRepository.class);
        var amounts = mock(SettingChangeAmountService.class);
        var service = new SettingChangeCancellationCompletionService(payments, attempts, allocations, refunds, amounts,
            mock(com.chapchap.subscription.global.kafka.customer.CustomerRefundEventPublisher.class));
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        PaymentTransaction original = PaymentTransaction.createFirstSubscriptionPayment(10L, 1L, 2L,
            10_000L, now, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28), "pay-key", now);
        ReflectionTestUtils.setField(original, "id", 100L); original.markAsSucceeded();
        Refund refund = Refund.createSettingChangeReduction(1L, 30L, 3_000L);
        ReflectionTestUtils.setField(refund, "id", 200L);
        PaymentTransaction cancellation = PaymentTransaction.createSettingChangeCancellation(10L, 1L, 2L,
            30L, 200L, 100L, 3_000L, now, LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 8), "cancel-key", now);
        ReflectionTestUtils.setField(cancellation, "id", 300L);
        PaymentAllocation allocation = PaymentAllocation.create(400L, 100L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 10_000L);
        Order oldOrder = mock(Order.class); when(oldOrder.getId()).thenReturn(400L);
        when(payments.findById(300L)).thenReturn(Optional.of(cancellation));
        when(payments.findById(100L)).thenReturn(Optional.of(original));
        when(refunds.findById(200L)).thenReturn(Optional.of(refund));
        when(attempts.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(300L)).thenReturn(List.of());
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(amounts.analyze(30L)).thenReturn(new SettingChangeAmountSnapshot(mock(com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting.class),
            List.of(oldOrder), List.of(), List.of(allocation), 10_000L, 7_000L));
        when(allocations.findAllByOriginalPaymentTransactionIdOrderByIdAsc(100L)).thenReturn(List.of(allocation));
        var execution = new PaymentCancellationExecutionResult(300L, PaymentProviderCode.PORTONE,
            "cancel-key", 3_000L, now, now.plusSeconds(1),
            PaymentCancellationResult.succeeded("external-pay", "external-cancel"));

        RefundStatus status = service.complete(30L, execution);

        assertThat(status).isEqualTo(RefundStatus.COMPLETED);
        assertThat(allocation.currentCancelableAmount()).isEqualTo(7_000L);
        assertThat(original.getCancelableAmount()).isEqualTo(7_000L);
        assertThat(refund.getSuccessfulRefundAmount()).isEqualTo(3_000L);
    }
}
