package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeOrderServiceTest {

    @Test
    void 일반_정기결제_기간의_변경대기_주문을_할인없이_저장한다() {
        OrderRepository orders = mock(OrderRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        SettingChangeOrderService service = new SettingChangeOrderService(orders, holidays);
        when(orders.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Order> saved = service.prepare(command(
            SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITHOUT_DISCOUNT,
            null
        ));

        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(orders).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CHANGE_PENDING);
            assertThat(order.getReplacementTargetOrderId()).isEqualTo(9L);
            assertThat(order.getMealAmount()).isEqualTo(20_000L);
            assertThat(order.getDiscountAmount()).isZero();
            assertThat(order.getActualAllocatedAmount()).isEqualTo(23_000L);
        });
        assertThat(saved).hasSize(1);
    }

    @Test
    void 첫_할인_기간의_가격영향_변경은_변경플랜_한개가격의_삼십퍼센트를_할인한다() {
        OrderRepository orders = mock(OrderRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        SettingChangeOrderService service = new SettingChangeOrderService(orders, holidays);
        when(orders.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Order> saved = service.prepare(command(
            SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITH_FIRST_DISCOUNT,
            null
        ));

        assertThat(saved).singleElement().satisfies(order -> {
            assertThat(order.getMealAmount()).isEqualTo(20_000L);
            assertThat(order.getDiscountAmount()).isEqualTo(3_000L);
            assertThat(order.getActualAllocatedAmount()).isEqualTo(20_000L);
        });
    }

    @Test
    void 가격비영향_변경은_기존주문의_금액과_할인을_그대로_승계한다() {
        OrderRepository orders = mock(OrderRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        SettingChangeOrderService service = new SettingChangeOrderService(orders, holidays);
        when(orders.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var snapshot = new SettingChangeOrderPreparationCommand.AmountSnapshot(
            8_900L, 2, 17_800L, 3_000L, 2_670L, 18_130L
        );

        List<Order> saved = service.prepare(command(
            SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER,
            snapshot
        ));

        assertThat(saved).singleElement().satisfies(order -> {
            assertThat(order.getMealUnitPrice()).isEqualTo(8_900L);
            assertThat(order.getMealQuantity()).isEqualTo(2);
            assertThat(order.getMealAmount()).isEqualTo(17_800L);
            assertThat(order.getDeliveryFee()).isEqualTo(3_000L);
            assertThat(order.getDiscountAmount()).isEqualTo(2_670L);
            assertThat(order.getActualAllocatedAmount()).isEqualTo(18_130L);
        });
    }

    private SettingChangeOrderPreparationCommand command(
        SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy,
        SettingChangeOrderPreparationCommand.AmountSnapshot amountSnapshot
    ) {
        return new SettingChangeOrderPreparationCommand(
            1L, 2L, 3L, 4L,
            new SettingChangeOrderPreparationCommand.PlanSnapshot(5L, "가정식", 10_000L),
            pricingPolicy,
            List.of(new SettingChangeOrderPreparationCommand.Delivery(
                6L, LocalDate.of(2026, 9, 9), 2, 9L, 10L, 5L, 9, "9일 메뉴", 2,
                amountSnapshot,
                new SettingChangeOrderPreparationCommand.AddressSnapshot(
                    7L, "수령인", "01012345678", "12345", "대구광역시", null,
                    "DIRECT", null, null
                ),
                OrderDeliveryTimeSlot.TIME_1100_1300
            ))
        );
    }
}
