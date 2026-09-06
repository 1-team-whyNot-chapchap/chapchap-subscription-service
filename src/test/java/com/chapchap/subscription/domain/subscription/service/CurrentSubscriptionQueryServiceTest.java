package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.response.CurrentSubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentSubscriptionQueryServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 20L;
    private static final Long SETTING_ID = 40L;
    private static final Long PLAN_ID = 50L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 28);

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPeriodRepository subscriptionPeriodRepository;
    @Mock private SubscriptionSettingRepository subscriptionSettingRepository;
    @Mock private SubscriptionDeliveryConditionRepository deliveryConditionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private KstReferenceTimeProvider referenceTimeProvider;

    private CurrentSubscriptionQueryService service;

    @BeforeEach
    void setUp() {
        service = new CurrentSubscriptionQueryService(
                subscriptionRepository,
                subscriptionPeriodRepository,
                subscriptionSettingRepository,
                deliveryConditionRepository,
                planRepository,
                addressRepository,
                referenceTimeProvider
        );
    }

    @Test
    void 구독이_없으면_null을_반환하고_다른_데이터를_조회하지_않는다() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getCurrentSubscription(USER_ID)).isNull();

        verifyNoInteractions(
                subscriptionPeriodRepository,
                subscriptionSettingRepository,
                deliveryConditionRepository,
                planRepository,
                addressRepository,
                referenceTimeProvider
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = SubscriptionStatus.class,
            names = {"PAYMENT_FAILED", "CANCELED_BEFORE_START", "ENDED"}
    )
    void 현재_이용할_수_없는_상태는_상태만_반환한다(SubscriptionStatus status) {
        Subscription subscription = subscription(status);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));

        CurrentSubscriptionResponse response = service.getCurrentSubscription(USER_ID);

        assertThat(response.subscriptionStatus()).isEqualTo(status);
        assertThat(response.periodStartDate()).isNull();
        assertThat(response.periodEndDate()).isNull();
        assertThat(response.plan()).isNull();
        assertThat(response.deliveryConditions()).isEmpty();
        verifyNoInteractions(
                subscriptionPeriodRepository,
                subscriptionSettingRepository,
                deliveryConditionRepository,
                planRepository,
                addressRepository,
                referenceTimeProvider
        );
    }

    @Test
    void 확정_대기는_최신_대기_기간과_대기_설정을_반환한다() {
        Subscription subscription = subscription(SubscriptionStatus.AWAITING_CONFIRMATION);
        SubscriptionPeriod period = period();
        SubscriptionSetting setting = setting();
        prepareCommonConfiguredResponse(subscription, period, setting);
        when(subscriptionPeriodRepository
                .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                        SUBSCRIPTION_ID,
                        SubscriptionPeriodStatus.AWAITING_CONFIRMATION
                ))
                .thenReturn(Optional.of(period));
        when(subscriptionSettingRepository
                .findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
                        SUBSCRIPTION_ID,
                        SubscriptionSettingStatus.AWAITING_CONFIRMATION
                ))
                .thenReturn(Optional.of(setting));

        CurrentSubscriptionResponse response = service.getCurrentSubscription(USER_ID);

        assertConfiguredResponse(response, SubscriptionStatus.AWAITING_CONFIRMATION);
        verifyNoInteractions(referenceTimeProvider);
    }

    @Test
    void 시작_예정은_기간_시작일에_적용되는_유효_설정을_반환한다() {
        Subscription subscription = subscription(SubscriptionStatus.SCHEDULED);
        SubscriptionPeriod period = period();
        SubscriptionSetting setting = setting();
        prepareCommonConfiguredResponse(subscription, period, setting);

        CurrentSubscriptionResponse response = service.getCurrentSubscription(USER_ID);

        assertConfiguredResponse(response, SubscriptionStatus.SCHEDULED);
        verifyNoInteractions(referenceTimeProvider);
    }

    @ParameterizedTest
    @EnumSource(
            value = SubscriptionStatus.class,
            names = {"IN_PROGRESS", "CANCELLATION_SCHEDULED"}
    )
    void 이용_중과_해지_예정은_KST_오늘의_기간과_설정을_반환한다(SubscriptionStatus status) {
        Subscription subscription = subscription(status);
        SubscriptionPeriod period = period();
        SubscriptionSetting setting = setting();
        prepareCommonConfiguredResponse(subscription, period, setting);
        when(referenceTimeProvider.now()).thenReturn(TODAY.atTime(12, 0));
        when(subscriptionPeriodRepository
                .findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
                        SUBSCRIPTION_ID,
                        SubscriptionPeriodStatus.IN_PROGRESS,
                        TODAY,
                        TODAY
                ))
                .thenReturn(Optional.of(period));
        when(subscriptionSettingRepository.findApplicableSettings(
                SUBSCRIPTION_ID,
                SubscriptionSettingStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(setting));

        CurrentSubscriptionResponse response = service.getCurrentSubscription(USER_ID);

        assertConfiguredResponse(response, status);
    }

    @Test
    void 중복된_현재_설정은_임의로_선택하지_않는다() {
        Subscription subscription = mock(Subscription.class);
        SubscriptionPeriod period = mock(SubscriptionPeriod.class);
        SubscriptionSetting firstSetting = mock(SubscriptionSetting.class);
        SubscriptionSetting secondSetting = mock(SubscriptionSetting.class);
        when(subscription.getStatus()).thenReturn(SubscriptionStatus.IN_PROGRESS);
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(referenceTimeProvider.now()).thenReturn(TODAY.atStartOfDay());
        when(subscriptionPeriodRepository
                .findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
                        SUBSCRIPTION_ID,
                        SubscriptionPeriodStatus.IN_PROGRESS,
                        TODAY,
                        TODAY
                ))
                .thenReturn(Optional.of(period));
        when(subscriptionSettingRepository.findApplicableSettings(
                SUBSCRIPTION_ID,
                SubscriptionSettingStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(firstSetting, secondSetting));

        assertThatThrownBy(() -> service.getCurrentSubscription(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 적용 설정");

        verifyNoInteractions(deliveryConditionRepository, planRepository, addressRepository);
    }

    @Test
    void 다른_고객의_배송지는_반환하지_않는다() {
        Subscription subscription = mock(Subscription.class);
        SubscriptionPeriod period = mock(SubscriptionPeriod.class);
        SubscriptionSetting setting = mock(SubscriptionSetting.class);
        SubscriptionDeliveryCondition condition = mock(SubscriptionDeliveryCondition.class);
        Address foreignAddress = mock(Address.class);
        Plan plan = mock(Plan.class);
        when(subscription.getStatus()).thenReturn(SubscriptionStatus.SCHEDULED);
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(period.getPeriodStartDate()).thenReturn(PERIOD_START);
        when(subscriptionPeriodRepository
                .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                        SUBSCRIPTION_ID,
                        SubscriptionPeriodStatus.SCHEDULED
                ))
                .thenReturn(Optional.of(period));
        when(subscriptionSettingRepository.findApplicableSettings(
                SUBSCRIPTION_ID,
                SubscriptionSettingStatus.ACTIVE,
                PERIOD_START
        )).thenReturn(List.of(setting));
        when(setting.getId()).thenReturn(SETTING_ID);
        when(setting.getPlanId()).thenReturn(PLAN_ID);
        when(condition.getAddressId()).thenReturn(101L);
        when(foreignAddress.getUserId()).thenReturn(999L);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(deliveryConditionRepository.findAllBySubscriptionSettingId(SETTING_ID))
                .thenReturn(List.of(condition));
        when(addressRepository.findAllById(anyList())).thenReturn(List.of(foreignAddress));

        assertThatThrownBy(() -> service.getCurrentSubscription(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("배송지 소유권");
    }

    @Test
    void 배송_조건은_월요일부터_토요일_순으로_반환한다() {
        Subscription subscription = subscription(SubscriptionStatus.SCHEDULED);
        SubscriptionPeriod period = period();
        SubscriptionSetting setting = setting();
        SubscriptionDeliveryCondition saturday = condition(DeliveryWeekday.SATURDAY, 102L);
        SubscriptionDeliveryCondition monday = condition(DeliveryWeekday.MONDAY, 101L);
        prepareQuerySelection(subscription, period, setting);
        Plan plan = plan();
        Address saturdayAddress = address(102L, USER_ID);
        Address mondayAddress = address(101L, USER_ID);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(deliveryConditionRepository.findAllBySubscriptionSettingId(SETTING_ID))
                .thenReturn(List.of(saturday, monday));
        when(addressRepository.findAllById(anyList()))
                .thenReturn(List.of(saturdayAddress, mondayAddress));

        CurrentSubscriptionResponse response = service.getCurrentSubscription(USER_ID);

        assertThat(response.deliveryConditions())
                .extracting(CurrentSubscriptionResponse.DeliveryConditionResponse::weekday)
                .containsExactly(DeliveryWeekday.MONDAY, DeliveryWeekday.SATURDAY);
    }

    private void prepareCommonConfiguredResponse(
            Subscription subscription,
            SubscriptionPeriod period,
            SubscriptionSetting setting
    ) {
        prepareQuerySelection(subscription, period, setting);
        Plan plan = plan();
        SubscriptionDeliveryCondition condition = condition(DeliveryWeekday.MONDAY, 101L);
        Address address = address(101L, USER_ID);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(deliveryConditionRepository.findAllBySubscriptionSettingId(SETTING_ID))
                .thenReturn(List.of(condition));
        when(addressRepository.findAllById(anyList()))
                .thenReturn(List.of(address));
    }

    private void prepareQuerySelection(
            Subscription subscription,
            SubscriptionPeriod period,
            SubscriptionSetting setting
    ) {
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));

        if (subscription.getStatus() == SubscriptionStatus.SCHEDULED) {
            when(subscriptionPeriodRepository
                    .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                            SUBSCRIPTION_ID,
                            SubscriptionPeriodStatus.SCHEDULED
                    ))
                    .thenReturn(Optional.of(period));
            when(subscriptionSettingRepository.findApplicableSettings(
                    SUBSCRIPTION_ID,
                    SubscriptionSettingStatus.ACTIVE,
                    PERIOD_START
            )).thenReturn(List.of(setting));
        }
    }

    private void assertConfiguredResponse(
            CurrentSubscriptionResponse response,
            SubscriptionStatus expectedStatus
    ) {
        assertThat(response.subscriptionId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(response.subscriptionStatus()).isEqualTo(expectedStatus);
        assertThat(response.periodStartDate()).isEqualTo(PERIOD_START);
        assertThat(response.periodEndDate()).isEqualTo(PERIOD_END);
        assertThat(response.plan().planId()).isEqualTo("22222222-2222-4222-8222-222222222222");
        assertThat(response.deliveryConditions()).hasSize(1);
        assertThat(response.deliveryConditions().getFirst().address().addressId())
                .isEqualTo("00000000-0000-4000-8000-000000000101");
    }

    private Subscription subscription(SubscriptionStatus status) {
        Subscription subscription = mock(Subscription.class);
        when(subscription.getPublicId()).thenReturn("11111111-1111-4111-8111-111111111111");
        when(subscription.getStatus()).thenReturn(status);
        return subscription;
    }

    private SubscriptionPeriod period() {
        SubscriptionPeriod period = mock(SubscriptionPeriod.class);
        when(period.getPeriodStartDate()).thenReturn(PERIOD_START);
        when(period.getPeriodEndDate()).thenReturn(PERIOD_END);
        return period;
    }

    private SubscriptionSetting setting() {
        SubscriptionSetting setting = mock(SubscriptionSetting.class);
        when(setting.getId()).thenReturn(SETTING_ID);
        when(setting.getPlanId()).thenReturn(PLAN_ID);
        return setting;
    }

    private Plan plan() {
        Plan plan = mock(Plan.class);
        when(plan.getPublicId()).thenReturn("22222222-2222-4222-8222-222222222222");
        when(plan.getName()).thenReturn("가정식");
        when(plan.getDescription()).thenReturn("플랜 소개");
        when(plan.getUnitPrice()).thenReturn(8_900L);
        return plan;
    }

    private SubscriptionDeliveryCondition condition(DeliveryWeekday weekday, Long addressId) {
        SubscriptionDeliveryCondition condition = mock(SubscriptionDeliveryCondition.class);
        when(condition.getDeliveryWeekday()).thenReturn(weekday);
        when(condition.getMealQuantity()).thenReturn(2);
        when(condition.getDeliveryTimeSlot()).thenReturn(DeliveryTimeSlot.TIME_1100_1300);
        when(condition.getAddressId()).thenReturn(addressId);
        return condition;
    }

    private Address address(Long id, Long userId) {
        Address address = mock(Address.class);
        when(address.getId()).thenReturn(id);
        when(address.getUserId()).thenReturn(userId);
        when(address.getPublicId()).thenReturn(String.format("00000000-0000-4000-8000-%012d", id));
        when(address.getName()).thenReturn("우리 집");
        when(address.getRecipientName()).thenReturn("홍길동");
        when(address.getRecipientPhone()).thenReturn("010-0000-0000");
        when(address.getPostalCode()).thenReturn("00000");
        when(address.getAddressLine1()).thenReturn("대구광역시");
        when(address.getAddressLine2()).thenReturn("101동 101호");
        return address;
    }
}
