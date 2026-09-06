package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingChangeAmountServiceTest {
    @Test
    void 기존_취소가능_배분합과_신규주문합으로_차액을_계산한다() {
        var settings = mock(SubscriptionSettingRepository.class);
        var orders = mock(OrderRepository.class);
        var allocations = mock(PaymentAllocationRepository.class);
        var service = new SettingChangeAmountService(settings, orders, allocations);
        var setting = SubscriptionSetting.createChangePending(1L, 2L, 2,
            LocalDateTime.of(2026, 9, 6, 12, 0), LocalDate.of(2026, 9, 7));
        ReflectionTestUtils.setField(setting, "id", 3L);
        Order oldOrder = mock(Order.class); when(oldOrder.getId()).thenReturn(10L);
        Order newOrder = mock(Order.class); when(newOrder.getActualAllocatedAmount()).thenReturn(13_000L);
        PaymentAllocation allocation = PaymentAllocation.create(10L, 20L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, 10_000L);
        when(settings.findById(3L)).thenReturn(Optional.of(setting));
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(
            1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, LocalDate.of(2026, 9, 7)))
            .thenReturn(List.of(oldOrder));
        when(orders.findAllBySubscriptionSettingId(3L)).thenReturn(List.of(newOrder));
        when(allocations.findAllByOrderIdInOrderByIdAsc(List.of(10L))).thenReturn(List.of(allocation));

        SettingChangeAmountSnapshot result = service.analyze(3L);

        assertThat(result.oldAmount()).isEqualTo(10_000L);
        assertThat(result.newAmount()).isEqualTo(13_000L);
        assertThat(result.differenceAmount()).isEqualTo(3_000L);
    }
}
