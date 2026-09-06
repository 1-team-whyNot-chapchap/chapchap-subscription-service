package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NextSubscriptionPeriodPreparationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionSettingRepository settingRepository;
    @Mock private SubscriptionDeliveryConditionRepository conditionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private TermsService termsService;
    @Mock private OrderRepository orderRepository;
    private NextSubscriptionPeriodPreparationService service;

    @BeforeEach
    void setUp() {
        service = new NextSubscriptionPeriodPreparationService(periodRepository, subscriptionRepository, settingRepository,
            conditionRepository, planRepository, menuRepository, addressRepository, holidayRepository, termsService,
            orderRepository);
    }

    @Test
    void 종료일인_이용중_구독은_다음_28일_기간과_할인없는_주문을_생성한다() {
        Subscription subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionPeriod current = SubscriptionPeriod.createAwaitingConfirmation(1L, 1, TODAY.minusDays(27), LocalDateTime.of(2026, 9, 1, 9, 0));
        ReflectionTestUtils.setField(current, "id", 2L);
        current.markScheduled(); current.start();
        SubscriptionSetting setting = SubscriptionSetting.createFirstAwaitingConfirmation(1L, 20L, TODAY.minusDays(27));
        ReflectionTestUtils.setField(setting, "id", 3L); setting.activate(LocalDateTime.of(2026, 9, 1, 9, 0));
        SubscriptionDeliveryCondition condition = SubscriptionDeliveryCondition.create(3L, DeliveryWeekday.THURSDAY, 2, 4L, DeliveryTimeSlot.TIME_1100_1300);
        Plan plan = mock(Plan.class); when(plan.getId()).thenReturn(20L); when(plan.getName()).thenReturn("PLAN"); when(plan.getUnitPrice()).thenReturn(10_000L);
        Menu menu = mock(Menu.class); when(menu.getId()).thenReturn(30L); when(menu.getName()).thenReturn("MENU");
        Address address = mock(Address.class); when(address.getId()).thenReturn(4L); when(address.getRecipientName()).thenReturn("수령인"); when(address.getRecipientPhone()).thenReturn("01012345678"); when(address.getPostalCode()).thenReturn("12345"); when(address.getAddressLine1()).thenReturn("주소"); when(address.getDeliveryMethodCode()).thenReturn("DIRECT");
        UserTermsAgreement agreement = mock(UserTermsAgreement.class); when(agreement.getId()).thenReturn(5L);
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(current));
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(1L)).thenReturn(Optional.of(current));
        when(settingRepository.findApplicableSettings(1L, SubscriptionSettingStatus.ACTIVE, TODAY)).thenReturn(List.of(setting));
        when(conditionRepository.findAllBySubscriptionSettingId(3L)).thenReturn(List.of(condition));
        when(periodRepository.save(any())).thenAnswer(invocation -> { SubscriptionPeriod next = invocation.getArgument(0); ReflectionTestUtils.setField(next, "id", 6L); return next; });
        when(planRepository.findById(20L)).thenReturn(Optional.of(plan)); when(termsService.requireCurrentAgreement(10L)).thenReturn(agreement);
        when(holidayRepository.findAllByHolidayDateBetween(any(), any())).thenReturn(List.of());
        when(menuRepository.findByPlanIdAndMenuSequence(eq(20L), anyInt())).thenReturn(Optional.of(menu));
        when(addressRepository.findById(4L)).thenReturn(Optional.of(address));
        when(orderRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Order> saved = invocation.getArgument(0);
            for (int index = 0; index < saved.size(); index++) {
                ReflectionTestUtils.setField(saved.get(index), "id", 100L + index);
            }
            return saved;
        });

        service.prepareIfDue(2L, TODAY, LocalDateTime.of(2026, 9, 30, 9, 0));

        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
            assertThat(order.getDiscountAmount()).isZero();
            assertThat(order.getSubscriptionPeriodId()).isEqualTo(6L);
        });
        assertThat(captor.getValue()).allMatch(order -> order.getDeliveryDate().getDayOfWeek() == java.time.DayOfWeek.THURSDAY);
    }

    @Test
    void 이미_다음_기간이_있으면_중복_생성하지_않는다() {
        SubscriptionPeriod current = mock(SubscriptionPeriod.class);
        when(current.getStatus()).thenReturn(SubscriptionPeriodStatus.IN_PROGRESS); when(current.getPeriodEndDate()).thenReturn(TODAY); when(current.getSubscriptionId()).thenReturn(1L); when(current.getPeriodSequence()).thenReturn(1);
        Subscription subscription = mock(Subscription.class); when(subscription.getStatus()).thenReturn(SubscriptionStatus.IN_PROGRESS); when(subscription.getId()).thenReturn(1L);
        SubscriptionPeriod existingNext = mock(SubscriptionPeriod.class); when(existingNext.getPeriodSequence()).thenReturn(2);
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(current)); when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(1L)).thenReturn(Optional.of(existingNext));

        service.prepareIfDue(2L, TODAY, LocalDateTime.now());

        verify(periodRepository, never()).save(any()); verify(orderRepository, never()).saveAll(any());
    }
}
