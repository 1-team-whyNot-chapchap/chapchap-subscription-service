package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeInProgressException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SettingChangeBaselineQueryServiceTest {
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class);
    private final SubscriptionDeliveryConditionRepository conditions = mock(SubscriptionDeliveryConditionRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final AddressRepository addresses = mock(AddressRepository.class);
    private final KstReferenceTimeProvider clock = mock(KstReferenceTimeProvider.class);
    private final SettingChangeBaselineQueryService service = new SettingChangeBaselineQueryService(
        subscriptions, settings, conditions, plans, addresses, clock, new SubscriptionScheduleCalculator());
    private final Subscription subscription = mock(Subscription.class);
    private final SubscriptionSetting latest = mock(SubscriptionSetting.class);

    @BeforeEach
    void setup() {
        when(subscriptions.findByUserId(1L)).thenReturn(Optional.of(subscription));
        when(subscription.getId()).thenReturn(10L);
        when(subscription.getPublicId()).thenReturn("public-subscription");
        when(subscription.getStatus()).thenReturn(SubscriptionStatus.IN_PROGRESS);
        when(settings.findTopBySubscriptionIdOrderBySettingSequenceDesc(10L)).thenReturn(Optional.of(latest));
        when(latest.getStatus()).thenReturn(SubscriptionSettingStatus.ACTIVE);
        when(clock.now()).thenReturn(LocalDateTime.parse("2026-09-14T15:00:00"));
    }

    private void configured(LocalDate date) {
        // 순번이 가장 큰 이력과 실제 유효 설정을 다르게 두어 최신 순번 사용을 탐지한다.
        var applicable = mock(SubscriptionSetting.class);
        when(applicable.getId()).thenReturn(20L);
        when(applicable.getPlanId()).thenReturn(30L);
        when(settings.findApplicableSettings(10L, SubscriptionSettingStatus.ACTIVE, date))
            .thenReturn(List.of(applicable));
        var plan = mock(Plan.class);
        when(plan.getPublicId()).thenReturn("future-plan");
        when(plan.getName()).thenReturn("간편식");
        when(plan.getUnitPrice()).thenReturn(7900L);
        when(plans.findById(30L)).thenReturn(Optional.of(plan));
        var condition = mock(SubscriptionDeliveryCondition.class);
        when(condition.getAddressId()).thenReturn(40L);
        when(condition.getDeliveryWeekday()).thenReturn(DeliveryWeekday.TUESDAY);
        when(condition.getDeliveryTimeSlot()).thenReturn(DeliveryTimeSlot.TIME_1100_1300);
        when(condition.getMealQuantity()).thenReturn(1);
        when(conditions.findAllBySubscriptionSettingId(20L)).thenReturn(List.of(condition));
        var address = mock(Address.class);
        when(address.getId()).thenReturn(40L);
        when(address.getUserId()).thenReturn(1L);
        when(address.getPublicId()).thenReturn("public-address");
        when(addresses.findAllById(List.of(40L))).thenReturn(List.of(address));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-14T13:59:59,2026-09-15",
        "2026-09-14T14:00:00,2026-09-16",
        "2026-09-18T14:00:00,2026-09-21",
        "2026-09-19T13:00:00,2026-09-21",
        "2026-09-14T23:59:59,2026-09-16",
        "2026-09-15T00:00:00,2026-09-16"
    })
    void 적용예정일의_유효설정을_읽고_최신순번_이력을_사용하지_않는다(String at, String expected) {
        var date = LocalDate.parse(expected);
        when(clock.now()).thenReturn(LocalDateTime.parse(at));
        configured(date);
        var response = service.getBaseline(1L);
        assertThat(response.effectiveStartDate()).isEqualTo(date);
        assertThat(response.plan().planId()).isEqualTo("future-plan");
        assertThat(response.deliveryConditions().getFirst().weekday()).isEqualTo(DeliveryWeekday.TUESDAY);
        verify(clock, times(1)).now();
        verify(settings, never()).save(any());
        verify(subscriptions, never()).save(any());
        verify(conditions, never()).save(any());
    }

    @Test
    void 구독없음은_변경대상없음이다() {
        when(subscriptions.findByUserId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(SubscriptionNotFoundException.class);
        verifyNoInteractions(clock, plans, addresses);
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "IN_PROGRESS")
    void 이용중이_아니면_차단한다(SubscriptionStatus status) {
        when(subscription.getStatus()).thenReturn(status);
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(SubscriptionChangeNotAllowedException.class);
    }

    @Test
    void 변경대기는_확정설정으로_우회하지_않는다() {
        when(latest.getStatus()).thenReturn(SubscriptionSettingStatus.CHANGE_PENDING);
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(SubscriptionChangeInProgressException.class);
        verifyNoInteractions(clock, plans);
    }

    @Test
    void 유효설정_누락과_중복은_오늘설정으로_대체하지_않는다() {
        var date = LocalDate.of(2026, 9, 16);
        when(settings.findApplicableSettings(10L, SubscriptionSettingStatus.ACTIVE, date)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(IllegalStateException.class);
        when(settings.findApplicableSettings(10L, SubscriptionSettingStatus.ACTIVE, date)).thenReturn(List.of(latest, latest));
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(plans, addresses);
    }

    @Test
    void 타인배송지는_노출하지_않는다() {
        configured(LocalDate.of(2026, 9, 16));
        var other = mock(Address.class);
        when(other.getUserId()).thenReturn(2L);
        when(addresses.findAllById(List.of(40L))).thenReturn(List.of(other));
        assertThatThrownBy(() -> service.getBaseline(1L)).isInstanceOf(IllegalStateException.class);
    }
}
