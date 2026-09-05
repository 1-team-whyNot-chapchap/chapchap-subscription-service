package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeCompletionServiceTest {

    @Test
    void 승인되면_기존주문을_비활성화하고_새설정을_활성화한다() {
        SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        SettingChangeCompletionService service = new SettingChangeCompletionService(settings, subscriptions, orders);
        SubscriptionSetting pending = pendingSetting();
        SubscriptionSetting previous = SubscriptionSetting.createFirstAwaitingConfirmation(1L, 1L, LocalDate.of(2026, 9, 1));
        previous.activate(LocalDateTime.of(2026, 9, 1, 0, 0));
        Order existingOrder = mock(Order.class);
        Order newOrder = mock(Order.class);
        when(settings.findWithLockById(2L)).thenReturn(Optional.of(pending));
        when(orders.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(newOrder));
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, LocalDate.of(2026, 9, 8))).thenReturn(List.of(existingOrder));
        when(settings.findApplicableSettings(1L, SubscriptionSettingStatus.ACTIVE, LocalDate.of(2026, 9, 8))).thenReturn(List.of(previous));

        service.complete(2L, SettingChangeCompletionStatus.APPROVED, LocalDateTime.of(2026, 9, 7, 13, 0));

        verify(existingOrder).inactivateForSettingChange();
        verify(newOrder).activateChange();
        assertThat(previous.getEffectiveEndExclusiveDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(pending.getStatus()).isEqualTo(SubscriptionSettingStatus.ACTIVE);
    }

    @Test
    void 미적용이면_기존주문은_그대로두고_새데이터만_미적용처리한다() {
        SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        SettingChangeCompletionService service = new SettingChangeCompletionService(settings, subscriptions, orders);
        SubscriptionSetting pending = pendingSetting();
        Order newOrder = mock(Order.class);
        when(settings.findWithLockById(2L)).thenReturn(Optional.of(pending));
        when(orders.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(newOrder));

        service.complete(2L, SettingChangeCompletionStatus.NOT_APPLIED, LocalDateTime.of(2026, 9, 7, 13, 0));

        verify(newOrder).markChangeNotApplied();
        assertThat(pending.getStatus()).isEqualTo(SubscriptionSettingStatus.CHANGE_NOT_APPLIED);
    }

    private SubscriptionSetting pendingSetting() {
        SubscriptionSetting setting = SubscriptionSetting.createChangePending(
            1L, 2L, 2, LocalDateTime.of(2026, 9, 7, 12, 0), LocalDate.of(2026, 9, 8)
        );
        ReflectionTestUtils.setField(setting, "id", 2L);
        return setting;
    }
}
