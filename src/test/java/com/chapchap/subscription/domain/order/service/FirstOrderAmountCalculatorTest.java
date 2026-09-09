package com.chapchap.subscription.domain.order.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirstOrderAmountCalculatorTest {

    @Test
    void 첫_할인은_배송일별_도시락_한개_단가에만_적용한다() {
        FirstOrderAmountCalculator.FirstOrderAmount result = FirstOrderAmountCalculator.calculate(8_900L, 3, true);

        assertThat(result.mealAmount()).isEqualTo(26_700L);
        assertThat(result.deliveryFee()).isEqualTo(3_000L);
        assertThat(result.discountAmount()).isEqualTo(2_670L);
        assertThat(result.actualAllocatedAmount()).isEqualTo(27_030L);
    }

    @Test
    void 할인_미적용_주문은_도시락_금액과_배송비를_전액_합산한다() {
        FirstOrderAmountCalculator.FirstOrderAmount result = FirstOrderAmountCalculator.calculate(8_900L, 3, false);

        assertThat(result.mealAmount()).isEqualTo(26_700L);
        assertThat(result.deliveryFee()).isEqualTo(3_000L);
        assertThat(result.discountAmount()).isZero();
        assertThat(result.actualAllocatedAmount()).isEqualTo(29_700L);
    }
}
