package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryConditionPolicyTest {

    private final DeliveryConditionPolicy policy = new DeliveryConditionPolicy();

    @Test
    void 서로_다른_요일의_배송_조건을_1개부터_6개까지_허용한다() {
        List<SubscriptionDeliveryCondition> conditions = List.of(
                condition(DeliveryWeekday.MONDAY),
                condition(DeliveryWeekday.WEDNESDAY),
                condition(DeliveryWeekday.FRIDAY)
        );

        assertThatCode(() -> policy.validate(conditions)).doesNotThrowAnyException();
    }

    @Test
    void 같은_배송_요일의_중복을_거부한다() {
        List<SubscriptionDeliveryCondition> conditions = List.of(
                condition(DeliveryWeekday.MONDAY),
                condition(DeliveryWeekday.MONDAY)
        );

        assertThatThrownBy(() -> policy.validate(conditions))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SubscriptionDeliveryCondition condition(DeliveryWeekday weekday) {
        return SubscriptionDeliveryCondition.create(
                1L,
                weekday,
                1,
                1L,
                DeliveryTimeSlot.TIME_1100_1300
        );
    }
}
