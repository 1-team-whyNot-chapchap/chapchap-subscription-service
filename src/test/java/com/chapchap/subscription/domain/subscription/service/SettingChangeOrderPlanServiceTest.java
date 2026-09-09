package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingChangeOrderPlanServiceTest {
    private final PlanRepository plans = mock(PlanRepository.class);
    private final MenuRepository menus = mock(MenuRepository.class);
    private final AddressRepository addresses = mock(AddressRepository.class);
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class);
    private final SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class);
    private final SubscriptionDeliveryConditionRepository conditions = mock(SubscriptionDeliveryConditionRepository.class);
    private final TermsService terms = mock(TermsService.class);
    private final SettingChangeOrderPlanService service = new SettingChangeOrderPlanService(
        plans, menus, addresses, holidays, orders, periods, settings, conditions, terms
    );

    private final LocalDate date = LocalDate.of(2026, 9, 7);
    private final SettingChangePreparationResult prepared = mock(SettingChangePreparationResult.class);
    private final Plan requestedPlan = mock(Plan.class);
    private final SubscriptionPeriod period = mock(SubscriptionPeriod.class);
    private final Order target = mock(Order.class);

    @BeforeEach
    void setUp() {
        when(prepared.userId()).thenReturn(10L);
        when(prepared.subscriptionId()).thenReturn(1L);
        when(requestedPlan.getId()).thenReturn(20L);
        when(requestedPlan.getName()).thenReturn("가정식");
        when(requestedPlan.getUnitPrice()).thenReturn(10_000L);
        when(plans.findById(20L)).thenReturn(Optional.of(requestedPlan));

        UserTermsAgreement agreement = mock(UserTermsAgreement.class);
        when(agreement.getId()).thenReturn(3L);
        when(terms.requireCurrentAgreement(10L)).thenReturn(agreement);

        when(period.getId()).thenReturn(5L);
        when(period.getPeriodEndDate()).thenReturn(date);
        when(periods.findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
            1L, SubscriptionPeriodStatus.IN_PROGRESS, date, date
        )).thenReturn(Optional.of(period));

        when(target.getDeliveryDate()).thenReturn(date);
        when(target.getId()).thenReturn(9L);
        when(target.getRevisionSequence()).thenReturn(1);
        when(target.getMealUnitPrice()).thenReturn(8_900L);
        when(target.getMealQuantity()).thenReturn(2);
        when(target.getMealAmount()).thenReturn(17_800L);
        when(target.getDeliveryFee()).thenReturn(3_000L);
        when(target.getDiscountAmount()).thenReturn(2_670L);
        when(target.getActualAllocatedAmount()).thenReturn(18_130L);
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(
            1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, date
        )).thenReturn(List.of(target));
        when(holidays.findAllByHolidayDateBetween(date, date)).thenReturn(List.of());

        SubscriptionSetting currentSetting = mock(SubscriptionSetting.class);
        when(currentSetting.getId()).thenReturn(2L);
        when(currentSetting.getPlanId()).thenReturn(20L);
        when(settings.findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
            1L, SubscriptionSettingStatus.ACTIVE
        )).thenReturn(Optional.of(currentSetting));

        Address address = mock(Address.class);
        when(address.getUserId()).thenReturn(10L);
        when(address.getId()).thenReturn(4L);
        when(address.getRecipientName()).thenReturn("수령인");
        when(address.getRecipientPhone()).thenReturn("01012345678");
        when(address.getPostalCode()).thenReturn("12345");
        when(address.getAddressLine1()).thenReturn("대구광역시");
        when(address.getDeliveryMethodCode()).thenReturn("DIRECT");
        when(addresses.findById(4L)).thenReturn(Optional.of(address));

        Menu menu = mock(Menu.class);
        when(menu.getId()).thenReturn(6L);
        when(menu.getPlanId()).thenReturn(20L);
        when(menu.getMenuSequence()).thenReturn(7);
        when(menu.getName()).thenReturn("월요일 메뉴");
        when(menus.findByPlanIdAndMenuSequence(20L, 7)).thenReturn(Optional.of(menu));
    }

    @Test
    void 배송지나_시간만_바뀌면_기존주문_금액을_승계하는_계획을_만든다() {
        when(prepared.draft()).thenReturn(draft(2));
        SubscriptionDeliveryCondition currentCondition = currentCondition(2);
        when(conditions.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(currentCondition));

        SettingChangeOrderPreparationCommand result = service.plan(prepared);

        assertThat(result.pricingPolicy())
            .isEqualTo(SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER);
        assertThat(result.deliveries()).singleElement().satisfies(delivery -> {
            assertThat(delivery.replacementTargetOrderId()).isEqualTo(9L);
            assertThat(delivery.revisionSequence()).isEqualTo(2);
            assertThat(delivery.replacementAmount().discountAmount()).isEqualTo(2_670L);
            assertThat(delivery.replacementAmount().actualAllocatedAmount()).isEqualTo(18_130L);
        });
    }

    @Test
    void 첫_할인_기간에_수량이_바뀌면_첫할인을_재계산하는_계획을_만든다() {
        when(prepared.draft()).thenReturn(draft(3));
        SubscriptionDeliveryCondition currentCondition = currentCondition(2);
        when(conditions.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(currentCondition));
        when(orders.findAllBySubscriptionPeriodId(5L)).thenReturn(List.of(target));

        SettingChangeOrderPreparationCommand result = service.plan(prepared);

        assertThat(result.pricingPolicy())
            .isEqualTo(SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITH_FIRST_DISCOUNT);
    }

    @Test
    void 일반_정기결제_기간에_수량이_바뀌면_할인없이_재계산하는_계획을_만든다() {
        when(prepared.draft()).thenReturn(draft(3));
        SubscriptionDeliveryCondition currentCondition = currentCondition(2);
        when(conditions.findAllBySubscriptionSettingId(2L)).thenReturn(List.of(currentCondition));
        Order regularOrder = mock(Order.class);
        when(regularOrder.getDiscountAmount()).thenReturn(0L);
        when(orders.findAllBySubscriptionPeriodId(5L)).thenReturn(List.of(regularOrder));

        SettingChangeOrderPreparationCommand result = service.plan(prepared);

        assertThat(result.pricingPolicy())
            .isEqualTo(SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITHOUT_DISCOUNT);
    }

    private SettingChangeDraft draft(int quantity) {
        return new SettingChangeDraft(2, 20L, date, List.of(
            new SettingChangeDraft.DeliveryCondition(
                DeliveryWeekday.MONDAY, quantity, 4L, DeliveryTimeSlot.TIME_1700_1900
            )
        ));
    }

    private SubscriptionDeliveryCondition currentCondition(int quantity) {
        SubscriptionDeliveryCondition condition = mock(SubscriptionDeliveryCondition.class);
        when(condition.getDeliveryWeekday()).thenReturn(DeliveryWeekday.MONDAY);
        when(condition.getMealQuantity()).thenReturn(quantity);
        return condition;
    }
}
