package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
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

class SettingChangeRefundPreparationServiceTest {
    @Test
    void 감액은_최근_설정변경_추가결제부터_취소거래를_준비한다() {
        var amounts = mock(SettingChangeAmountService.class);
        var refunds = mock(RefundRepository.class);
        var payments = mock(PaymentTransactionRepository.class);
        var periods = mock(SubscriptionPeriodRepository.class);
        var time = mock(KstReferenceTimeProvider.class);
        var service = new SettingChangeRefundPreparationService(amounts, refunds, payments, periods, time);
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        SubscriptionSetting setting = SubscriptionSetting.createChangePending(1L, 2L, 3, now, LocalDate.of(2026, 9, 8));
        ReflectionTestUtils.setField(setting, "id", 30L);
        PaymentTransaction first = original(100L, false, now.minusDays(10));
        PaymentTransaction change = original(101L, true, now.minusDays(1));
        PaymentAllocation firstAllocation = PaymentAllocation.create(10L, 100L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 5_000L);
        PaymentAllocation changeAllocation = PaymentAllocation.create(11L, 101L,
            PaymentAllocationType.SETTING_CHANGE_PAYMENT, 3_000L);
        when(amounts.analyze(30L)).thenReturn(new SettingChangeAmountSnapshot(setting,
            List.of(mock(Order.class)), List.of(mock(Order.class)),
            List.of(firstAllocation, changeAllocation), 8_000L, 6_000L));
        when(refunds.findBySubscriptionSettingId(30L)).thenReturn(Optional.empty());
        when(refunds.saveAndFlush(any())).thenAnswer(invocation -> {
            Object refund = invocation.getArgument(0); ReflectionTestUtils.setField(refund, "id", 40L); return refund;
        });
        when(payments.findAllById(any())).thenReturn(List.of(first, change));
        SubscriptionPeriod period = mock(SubscriptionPeriod.class);
        when(period.getId()).thenReturn(3L);
        when(period.getPeriodStartDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(period.getPeriodEndDate()).thenReturn(LocalDate.of(2026, 9, 28));
        when(periods.findById(3L)).thenReturn(Optional.of(period));
        when(time.now()).thenReturn(now);
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentTransaction payment = invocation.getArgument(0); ReflectionTestUtils.setField(payment, "id", 50L); return payment;
        });

        PreparedSettingChangeRefund result = service.prepareNext(30L);

        assertThat(result.cancellationTransactionId()).isEqualTo(50L);
        var saved = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        org.mockito.Mockito.verify(payments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOriginalPaymentTransactionId()).isEqualTo(101L);
        assertThat(saved.getValue().getTransactionAmount()).isEqualTo(2_000L);
        assertThat(saved.getValue().getTransactionType()).isEqualTo(PaymentTransactionType.SETTING_CHANGE_PARTIAL_CANCELLATION);
    }

    private PaymentTransaction original(Long id, boolean settingChange, LocalDateTime occurredAt) {
        PaymentTransaction payment = settingChange
            ? PaymentTransaction.createSettingChangePayment(10L, 1L, 3L, 20L, 3_000L, occurredAt,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 2),
                "key-" + id, occurredAt)
            : PaymentTransaction.createFirstSubscriptionPayment(10L, 1L, 3L, 5_000L, occurredAt,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28), "key-" + id, occurredAt);
        ReflectionTestUtils.setField(payment, "id", id);
        payment.markAsSucceeded();
        return payment;
    }
}
