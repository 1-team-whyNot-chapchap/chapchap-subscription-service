package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeInProgressException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingChangePreparationServiceTest {
    @Test
    void 이용중_구독과_유효한_배송조건을_검증해_내부계획을_반환한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class); PlanRepository plans = mock(PlanRepository.class); AddressService addresses = mock(AddressService.class); KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class); OrderRepository orders = mock(OrderRepository.class);
        SettingChangePreparationService service = new SettingChangePreparationService(subscriptions, settings, plans, addresses, time, orders);
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionSetting setting = SubscriptionSetting.createFirstAwaitingConfirmation(1L, 20L, LocalDate.now()); ReflectionTestUtils.setField(setting, "id", 2L);
        Plan plan = mock(Plan.class); when(plan.getId()).thenReturn(3L);
        Address address = mock(Address.class); when(address.getId()).thenReturn(4L);
        Order replaceableOrder = mock(Order.class); when(replaceableOrder.getId()).thenReturn(9L);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription)); when(settings.findTopBySubscriptionIdOrderBySettingSequenceDesc(1L)).thenReturn(Optional.of(setting)); when(plans.findByPublicId("11111111-1111-4111-8111-111111111111")).thenReturn(Optional.of(plan)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,7,10,0));
        when(addresses.requireActiveAddress(10L, "21111111-1111-4111-8111-111111111111")).thenReturn(address);
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, LocalDate.of(2026,9,8))).thenReturn(List.of(replaceableOrder));
        SettingChangePreparationResult result = service.prepare(10L, new SettingChangePreparationRequest("11111111-1111-4111-8111-111111111111", List.of(new SettingChangePreparationRequest.DeliveryCondition(DeliveryWeekday.MONDAY, 2, "21111111-1111-4111-8111-111111111111", DeliveryTimeSlot.TIME_1100_1300))));
        assertThat(result.subscriptionId()).isEqualTo(1L); assertThat(result.currentSettingId()).isEqualTo(2L); assertThat(result.requestedPlanId()).isEqualTo(3L); assertThat(result.effectiveStartDate()).isEqualTo(LocalDate.of(2026,9,8)); assertThat(result.draft().settingSequence()).isEqualTo(2); assertThat(result.draft().deliveryConditions()).extracting(SettingChangeDraft.DeliveryCondition::addressId).containsExactly(4L); assertThat(result.replaceableOrderIds()).containsExactly(9L); verify(addresses).requireActiveAddress(10L, "21111111-1111-4111-8111-111111111111");
    }

    @Test
    void 변경대기_설정이_있으면_새_변경을_차단한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class); SettingChangePreparationService service = new SettingChangePreparationService(subscriptions, settings, mock(PlanRepository.class), mock(AddressService.class), mock(KstReferenceTimeProvider.class), mock(OrderRepository.class));
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionSetting setting = SubscriptionSetting.createAwaitingConfirmation(1L, 20L, 2, LocalDateTime.now(), LocalDate.now()); ReflectionTestUtils.setField(setting, "status", SubscriptionSettingStatus.CHANGE_PENDING);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription)); when(settings.findTopBySubscriptionIdOrderBySettingSequenceDesc(1L)).thenReturn(Optional.of(setting));
        assertThatThrownBy(() -> service.prepare(10L, new SettingChangePreparationRequest("11111111-1111-4111-8111-111111111111", List.of(new SettingChangePreparationRequest.DeliveryCondition(DeliveryWeekday.MONDAY, 1, "21111111-1111-4111-8111-111111111111", DeliveryTimeSlot.TIME_1100_1300)))))
            .isInstanceOf(SubscriptionChangeInProgressException.class);
    }

    @Test
    void 오후_두시부터는_다다음날을_변경적용일로_계산한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class); PlanRepository plans = mock(PlanRepository.class); AddressService addresses = mock(AddressService.class); KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class); OrderRepository orders = mock(OrderRepository.class);
        SettingChangePreparationService service = new SettingChangePreparationService(subscriptions, settings, plans, addresses, time, orders);
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionSetting setting = SubscriptionSetting.createFirstAwaitingConfirmation(1L, 20L, LocalDate.now()); ReflectionTestUtils.setField(setting, "id", 2L);
        Plan plan = mock(Plan.class); when(plan.getId()).thenReturn(3L);
        Address address = mock(Address.class); when(address.getId()).thenReturn(4L);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription)); when(settings.findTopBySubscriptionIdOrderBySettingSequenceDesc(1L)).thenReturn(Optional.of(setting)); when(plans.findByPublicId("11111111-1111-4111-8111-111111111111")).thenReturn(Optional.of(plan)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,8,14,0));
        when(addresses.requireActiveAddress(10L, "21111111-1111-4111-8111-111111111111")).thenReturn(address);
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, LocalDate.of(2026,9,10))).thenReturn(List.of());
        SettingChangePreparationResult result = service.prepare(10L, new SettingChangePreparationRequest("11111111-1111-4111-8111-111111111111", List.of(new SettingChangePreparationRequest.DeliveryCondition(DeliveryWeekday.MONDAY, 1, "21111111-1111-4111-8111-111111111111", DeliveryTimeSlot.TIME_1100_1300))));
        assertThat(result.effectiveStartDate()).isEqualTo(LocalDate.of(2026,9,10));
    }
}
