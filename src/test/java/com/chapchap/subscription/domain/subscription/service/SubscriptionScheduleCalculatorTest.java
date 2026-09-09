package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionScheduleCalculatorTest {

    private final SubscriptionScheduleCalculator calculator = new SubscriptionScheduleCalculator();

    @Test
    void 오후_2시_전이면_다음_날을_반영_기준일로_계산한다() {
        LocalDateTime referenceAt = LocalDateTime.of(2026, 9, 1, 13, 59, 59);

        SubscriptionSchedule result = calculator.calculate(
                referenceAt,
                Set.of(DeliveryWeekday.WEDNESDAY),
                Set.of()
        );

        assertThat(result.reflectionDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(result.periodEndDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(result.deliveryDates()).containsExactly(
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 9, 23)
        );
    }

    @Test
    void 오후_2시부터는_다다음_날을_반영_기준일로_계산한다() {
        LocalDateTime referenceAt = LocalDateTime.of(2026, 9, 1, 14, 0);

        SubscriptionSchedule result = calculator.calculate(
                referenceAt,
                Set.of(DeliveryWeekday.THURSDAY),
                Set.of()
        );

        assertThat(result.reflectionDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void 반영_기준일이_일요일이면_월요일로_조정한다() {
        LocalDateTime referenceAt = LocalDateTime.of(2026, 9, 4, 15, 0);

        SubscriptionSchedule result = calculator.calculate(
                referenceAt,
                Set.of(DeliveryWeekday.MONDAY),
                Set.of()
        );

        assertThat(result.reflectionDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(result.periodStartDate()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void 선택_요일의_공휴일은_첫_배송일과_기간_배송일에서_제외한다() {
        LocalDateTime referenceAt = LocalDateTime.of(2026, 8, 15, 13, 0);
        LocalDate substituteHoliday = LocalDate.of(2026, 8, 17);

        SubscriptionSchedule result = calculator.calculate(
                referenceAt,
                Set.of(DeliveryWeekday.MONDAY, DeliveryWeekday.TUESDAY),
                Set.of(substituteHoliday)
        );

        assertThat(result.reflectionDate()).isEqualTo(substituteHoliday);
        assertThat(result.periodStartDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(result.deliveryDates()).doesNotContain(substituteHoliday);
    }

    @Test
    void 배송일의_달력_일자를_메뉴_순번으로_사용한다() {
        assertThat(calculator.menuSequence(LocalDate.of(2026, 9, 30))).isEqualTo(30);
    }

    @Test
    void 배송_요일이_없으면_계산하지_않는다() {
        assertThatThrownBy(() -> calculator.calculate(
                LocalDateTime.of(2026, 9, 1, 10, 0),
                Set.of(),
                Set.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
