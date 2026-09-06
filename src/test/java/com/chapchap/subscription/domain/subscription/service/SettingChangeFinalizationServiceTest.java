package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeFinalizationServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void 기존_결제잔액을_신규주문에_재배분한_뒤_상태를_확정한다() {
        var amounts = mock(SettingChangeAmountService.class);
        var allocations = mock(PaymentAllocationRepository.class);
        var payments = mock(PaymentTransactionRepository.class);
        var completion = mock(SettingChangeCompletionService.class);
        var service = new SettingChangeFinalizationService(amounts, allocations, payments, completion);
        Order oldOrder = order(10L, LocalDate.of(2026, 9, 8), 10_000L);
        Order secondOldOrder = order(13L, LocalDate.of(2026, 9, 9), 10_000L);
        Order first = order(11L, LocalDate.of(2026, 9, 9), 4_000L);
        Order second = order(12L, LocalDate.of(2026, 9, 10), 6_000L);
        PaymentAllocation firstSource = PaymentAllocation.create(10L, 20L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 4_000L);
        PaymentAllocation secondSource = PaymentAllocation.create(13L, 20L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 6_000L);
        PaymentTransaction original = PaymentTransaction.createFirstSubscriptionPayment(1L, 2L, 3L,
            10_000L, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 28), "key", LocalDateTime.of(2026, 9, 1, 0, 0));
        ReflectionTestUtils.setField(original, "id", 20L); original.markAsSucceeded();
        var setting = mock(SubscriptionSetting.class);
        when(setting.getSubscriptionId()).thenReturn(2L);
        when(amounts.analyze(30L)).thenReturn(new SettingChangeAmountSnapshot(setting,
            List.of(oldOrder, secondOldOrder), List.of(first, second),
            List.of(firstSource, secondSource), 10_000L, 10_000L));
        when(allocations.findAllByOrderIdInOrderByIdAsc(List.of(11L, 12L))).thenReturn(List.of());
        when(payments.findAllById(anyList())).thenReturn(List.of(original));
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 6, 13, 0);

        service.approve(30L, completedAt);

        ArgumentCaptor<List<PaymentAllocation>> saved = ArgumentCaptor.forClass(List.class);
        verify(allocations).deleteAllByOrderIdIn(List.of(10L, 13L));
        verify(allocations).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(PaymentAllocation::getAllocatedAmount)
            .containsExactly(4_000L, 6_000L);
        verify(completion).complete(30L, SettingChangeCompletionStatus.APPROVED, completedAt);
    }

    private Order order(Long id, LocalDate date, Long amount) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getDeliveryDate()).thenReturn(date);
        when(order.getActualAllocatedAmount()).thenReturn(amount);
        return order;
    }
}
