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
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingChangeOrderPlanServiceTest {

    @Test
    void 적용일의_선택요일에_기존주문을_대체하는_변경주문_계획을_만든다() {
        PlanRepository plans = mock(PlanRepository.class);
        MenuRepository menus = mock(MenuRepository.class);
        AddressRepository addresses = mock(AddressRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class);
        TermsService terms = mock(TermsService.class);
        SettingChangeOrderPlanService service = new SettingChangeOrderPlanService(
            plans, menus, addresses, holidays, orders, periods, terms
        );
        LocalDate date = LocalDate.of(2026, 9, 7);
        SettingChangeDraft draft = new SettingChangeDraft(2, 2L, date, List.of(
            new SettingChangeDraft.DeliveryCondition(DeliveryWeekday.MONDAY, 2, 4L, DeliveryTimeSlot.TIME_1100_1300)
        ));
        SettingChangePreparationResult prepared = mock(SettingChangePreparationResult.class);
        when(prepared.userId()).thenReturn(10L);
        when(prepared.subscriptionId()).thenReturn(1L);
        when(prepared.draft()).thenReturn(draft);
        Plan plan = mock(Plan.class); when(plan.getId()).thenReturn(2L); when(plan.getName()).thenReturn("가정식"); when(plan.getUnitPrice()).thenReturn(10_000L);
        when(plans.findById(2L)).thenReturn(Optional.of(plan));
        UserTermsAgreement agreement = mock(UserTermsAgreement.class); when(agreement.getId()).thenReturn(3L); when(terms.requireCurrentAgreement(10L)).thenReturn(agreement);
        SubscriptionPeriod period = mock(SubscriptionPeriod.class); when(period.getId()).thenReturn(5L); when(period.getPeriodEndDate()).thenReturn(date);
        when(periods.findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(1L, SubscriptionPeriodStatus.IN_PROGRESS, date, date)).thenReturn(Optional.of(period));
        Order target = mock(Order.class); when(target.getDeliveryDate()).thenReturn(date); when(target.getId()).thenReturn(9L); when(target.getRevisionSequence()).thenReturn(1);
        when(orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, date)).thenReturn(List.of(target));
        when(holidays.findAllByHolidayDateBetween(date, date)).thenReturn(List.of());
        Address address = mock(Address.class); when(address.getUserId()).thenReturn(10L); when(address.getDeletedAt()).thenReturn(null); when(address.getId()).thenReturn(4L); when(address.getRecipientName()).thenReturn("수령인"); when(address.getRecipientPhone()).thenReturn("01012345678"); when(address.getPostalCode()).thenReturn("12345"); when(address.getAddressLine1()).thenReturn("대구광역시"); when(address.getDeliveryMethodCode()).thenReturn("DIRECT");
        when(addresses.findById(4L)).thenReturn(Optional.of(address));
        Menu menu = mock(Menu.class); when(menu.getId()).thenReturn(6L); when(menu.getPlanId()).thenReturn(2L); when(menu.getMenuSequence()).thenReturn(7); when(menu.getName()).thenReturn("월요일 메뉴");
        when(menus.findByPlanIdAndMenuSequence(2L, 7)).thenReturn(Optional.of(menu));

        SettingChangeOrderPreparationCommand result = service.plan(prepared);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.subscriptionId()).isEqualTo(1L);
        assertThat(result.subscriptionSettingId()).isNull();
        assertThat(result.deliveries()).singleElement().satisfies(delivery -> {
            assertThat(delivery.deliveryDate()).isEqualTo(date);
            assertThat(delivery.replacementTargetOrderId()).isEqualTo(9L);
            assertThat(delivery.revisionSequence()).isEqualTo(2);
        });
    }
}
