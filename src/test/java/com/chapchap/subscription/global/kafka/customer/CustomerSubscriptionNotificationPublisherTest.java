package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSubscriptionNotificationPublisherTest {
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private SubscriptionRepository subscriptions;
    @Mock private SubscriptionPeriodRepository periods;
    @Mock private PlanRepository plans;
    @Mock private SubscriptionDeliveryConditionRepository conditions;
    @Mock private AddressRepository addresses;
    @Mock private Subscription subscription;
    private CustomerSubscriptionNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        CustomerSubscriptionNotificationKafkaProperties properties =
            new CustomerSubscriptionNotificationKafkaProperties();
        properties.setTopic("subscription.customer-notification-events.v1");
        publisher = new CustomerSubscriptionNotificationPublisher(
            kafkaTemplate, properties, subscriptions, periods, plans, conditions, addresses
        );
        org.mockito.Mockito.lenient().when(subscription.getId()).thenReturn(1L);
        org.mockito.Mockito.lenient().when(subscription.getPublicId()).thenReturn("44444444-4444-4444-8444-444444444444");
        org.mockito.Mockito.lenient().when(subscription.getUserId()).thenReturn(25L);
    }

    @Test
    void 설정변경은_플랜과_요일별_배송조건의_고객표시_스냅샷을_발행한다() {
        SubscriptionSetting setting = org.mockito.Mockito.mock(SubscriptionSetting.class);
        Plan plan = org.mockito.Mockito.mock(Plan.class);
        SubscriptionDeliveryCondition condition = org.mockito.Mockito.mock(SubscriptionDeliveryCondition.class);
        Address address = org.mockito.Mockito.mock(Address.class);
        when(setting.getSubscriptionId()).thenReturn(1L);
        when(setting.getId()).thenReturn(2L);
        when(setting.getPlanId()).thenReturn(3L);
        when(setting.getSettingSequence()).thenReturn(4);
        when(setting.getEffectiveStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));
        when(plans.findById(3L)).thenReturn(Optional.of(plan));
        when(plan.getName()).thenReturn("주 3회 플랜");
        when(conditions.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(condition));
        when(condition.getDeliveryWeekday()).thenReturn(DeliveryWeekday.MONDAY);
        when(condition.getMealQuantity()).thenReturn(1);
        when(condition.getAddressId()).thenReturn(5L);
        when(condition.getDeliveryTimeSlot()).thenReturn(DeliveryTimeSlot.TIME_1100_1300);
        when(addresses.findById(5L)).thenReturn(Optional.of(address));
        when(address.getName()).thenReturn("회사");

        publisher.publishSettingChangedAfterCommit(setting, LocalDateTime.of(2026, 9, 6, 16, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq("subscription.customer-notification-events.v1"),
            org.mockito.ArgumentMatchers.eq("25"),
            eventCaptor.capture()
        );
        SubscriptionSettingChangedEvent event = (SubscriptionSettingChangedEvent) eventCaptor.getValue();
        assertThat(event.data().settingVersion()).isEqualTo(4);
        assertThat(event.data().planDisplayName()).isEqualTo("주 3회 플랜");
        assertThat(event.data().deliveryConditions()).containsExactly(
            new SubscriptionSettingChangedEvent.DeliveryCondition(
                "MONDAY", 1, "회사", "TIME_1100_1300"
            )
        );
    }

    @Test
    void 시작전_취소는_null_마지막이용일과_완료_환불결과를_발행한다() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 6, 16, 0);
        LocalDateTime confirmedAt = requestedAt.plusMinutes(1);

        publisher.publishCancellationConfirmedAfterCommit(
            subscription, "CANCELLATION_BEFORE_START", requestedAt, confirmedAt,
            null, "COMPLETED", confirmedAt
        );

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("25"), eventCaptor.capture()
        );
        SubscriptionCancellationConfirmedEvent event =
            (SubscriptionCancellationConfirmedEvent) eventCaptor.getValue();
        assertThat(event.data().lastUseDate()).isNull();
        assertThat(event.data().effectiveAt().toLocalDateTime()).isEqualTo(confirmedAt);
        assertThat(event.data().refundResult()).isEqualTo("COMPLETED");
    }

    @Test
    void 실제종료는_고객해지_사유와_userId_키로_발행한다() {
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 29, 0, 0);

        publisher.publishEndedAfterCommit(subscription, "CUSTOMER_CANCELLATION", endedAt);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq("subscription.customer-notification-events.v1"),
            org.mockito.ArgumentMatchers.eq("25"),
            eventCaptor.capture()
        );
        SubscriptionEndedEvent event = (SubscriptionEndedEvent) eventCaptor.getValue();
        assertThat(event.data().endReason()).isEqualTo("CUSTOMER_CANCELLATION");
        assertThat(event.data().endedAt().toLocalDateTime()).isEqualTo(endedAt);
    }

    @Test
    void 다음기간_해지는_현재기간_종료일과_다음날_자정을_발행한다() {
        SubscriptionPeriod current = org.mockito.Mockito.mock(SubscriptionPeriod.class);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 6, 11, 0);
        when(subscription.getCancellationRequestedAt()).thenReturn(requestedAt);
        when(current.getPeriodEndDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
            1L, SubscriptionPeriodStatus.IN_PROGRESS
        )).thenReturn(Optional.of(current));

        publisher.publishNextPeriodCancellationAfterCommit(
            subscription, "NOT_REQUIRED", requestedAt
        );

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("25"), eventCaptor.capture()
        );
        SubscriptionCancellationConfirmedEvent event =
            (SubscriptionCancellationConfirmedEvent) eventCaptor.getValue();
        assertThat(event.data().lastUseDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(event.data().effectiveAt().toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(event.data().refundResult()).isEqualTo("NOT_REQUIRED");
    }
}
