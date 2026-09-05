package com.chapchap.subscription.domain.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSettingChangeTest {

    @Test
    void 변경대기_주문은_대체대상과_개정순번을_보존한다() {
        Order order = changePendingOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CHANGE_PENDING);
        assertThat(order.getReplacementTargetOrderId()).isEqualTo(9L);
        assertThat(order.getRevisionSequence()).isEqualTo(2);
    }

    @Test
    void 변경대기_주문은_확정되면_유효가_된다() {
        Order order = changePendingOrder();

        order.activateChange();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACTIVE);
    }

    @Test
    void 변경대기_주문은_실패하면_변경미적용으로_기록한다() {
        Order order = changePendingOrder();

        order.markChangeNotApplied();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CHANGE_NOT_APPLIED);
    }

    private Order changePendingOrder() {
        return Order.createChangePending(
            1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, LocalDate.of(2026, 9, 9), 2, 9L,
            "가정식", "9일 메뉴", 10_000L, 2, 20_000L, 3_000L, 0L, 23_000L,
            "수령인", "01012345678", "12345", "대구광역시", null, "DIRECT", null, null,
            OrderDeliveryTimeSlot.TIME_1100_1300
        );
    }
}
