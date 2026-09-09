package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.service.FirstOrderService;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.FirstPaymentPreparationService;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionPreviewResponse;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.exception.subscription.SubscriptionAlreadyActiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstSubscriptionPreviewTest {
    private static final long USER_ID = 10L;
    private static final String PLAN_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ADDRESS_ID = "22222222-2222-4222-8222-222222222222";

    @Mock private TermsService termsService;
    @Mock private AddressService addressService;
    @Mock private PlanRepository planRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private SubscriptionSettingRepository settingRepository;
    @Mock private SubscriptionDeliveryConditionRepository conditionRepository;
    @Mock private SubscriptionStatusHistoryRepository historyRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private KstReferenceTimeProvider timeProvider;
    @Mock private FirstOrderService firstOrderService;
    @Mock private FirstPaymentPreparationService firstPaymentPreparationService;

    private FirstSubscriptionPreparationService service;

    @BeforeEach
    void setUp() {
        service = new FirstSubscriptionPreparationService(
            termsService, addressService, planRepository, menuRepository, holidayRepository,
            subscriptionRepository, periodRepository, settingRepository, conditionRepository,
            historyRepository, paymentMethodRepository, paymentTransactionRepository,
            new SubscriptionScheduleCalculator(), timeProvider, firstOrderService, firstPaymentPreparationService
        );
    }

    @Test
    void 예상금액은_실제_첫주문과_같은_기간_할인_산식으로_계산하고_업무데이터를_저장하지_않는다() {
        preparePreviewDependencies();

        FirstSubscriptionPreviewResponse response = service.preview(USER_ID, request());

        assertThat(response.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.periodEndDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(response.totalMealAmount()).isEqualTo(71_200L);
        assertThat(response.totalDeliveryFee()).isEqualTo(12_000L);
        assertThat(response.totalDiscountAmount()).isEqualTo(10_680L);
        assertThat(response.paymentAmount()).isEqualTo(72_520L);

        verify(subscriptionRepository).findByUserId(USER_ID);
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(
            periodRepository, settingRepository, conditionRepository, historyRepository,
            paymentTransactionRepository, firstOrderService, firstPaymentPreparationService
        );
    }

    @Test
    void 진행중_구독이_있으면_계산이나_저장없이_거절한다() {
        Subscription subscription = Subscription.create(USER_ID);
        subscription.markScheduled();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.preview(USER_ID, request()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verifyNoInteractions(
            termsService, addressService, planRepository, menuRepository, holidayRepository,
            paymentMethodRepository, periodRepository, settingRepository, conditionRepository,
            historyRepository, paymentTransactionRepository, firstOrderService, firstPaymentPreparationService
        );
    }

    private void preparePreviewDependencies() {
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(101L);
        when(plan.getUnitPrice()).thenReturn(8_900L);

        Menu menu = mock(Menu.class);
        when(menu.getId()).thenReturn(201L);
        when(menu.getPlanId()).thenReturn(101L);
        when(menu.getMenuSequence()).thenReturn(1);
        when(menu.getName()).thenReturn("테스트 메뉴");

        Address address = mock(Address.class);
        when(address.getId()).thenReturn(301L);
        when(address.getRecipientName()).thenReturn("테스트 수령인");
        when(address.getRecipientPhone()).thenReturn("010-0000-0000");
        when(address.getPostalCode()).thenReturn("00000");
        when(address.getAddressLine1()).thenReturn("대구광역시 테스트로 1");
        when(address.getDeliveryMethodCode()).thenReturn("DIRECT");

        when(timeProvider.now()).thenReturn(LocalDateTime.of(2026, 9, 9, 10, 0));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(termsService.requireCurrentAgreement(USER_ID)).thenReturn(mock(UserTermsAgreement.class));
        when(planRepository.findByPublicId(PLAN_ID)).thenReturn(Optional.of(plan));
        when(addressService.requireActiveAddress(USER_ID, ADDRESS_ID)).thenReturn(address);
        when(paymentMethodRepository.existsByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
            USER_ID, PaymentMethodStatus.AVAILABLE
        )).thenReturn(true);
        when(holidayRepository.findAllByHolidayDateBetween(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        when(menuRepository.findByPlanIdAndMenuSequence(eq(101L), anyInt())).thenReturn(Optional.of(menu));
    }

    private FirstSubscriptionRequest request() {
        return new FirstSubscriptionRequest(
            PLAN_ID,
            List.of(new FirstSubscriptionRequest.DeliveryCondition(
                DeliveryWeekday.MONDAY, 2, ADDRESS_ID, DeliveryTimeSlot.TIME_1100_1300
            ))
        );
    }
}
