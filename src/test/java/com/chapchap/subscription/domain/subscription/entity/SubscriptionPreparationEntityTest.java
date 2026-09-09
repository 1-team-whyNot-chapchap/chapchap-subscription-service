package com.chapchap.subscription.domain.subscription.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionPreparationEntityTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 9, 3, 13, 0);
    private static final LocalDate PERIOD_START_DATE = LocalDate.of(2026, 9, 4);

    @Test
    void 이용_기간은_시작일을_포함한_28일로_생성한다() {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
                1L,
                1,
                PERIOD_START_DATE,
                REFERENCE_AT
        );

        assertThat(period.getPeriodEndDate()).isEqualTo(PERIOD_START_DATE.plusDays(27));
        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void 결제_성공_시_기간과_설정을_확정한다() {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
                1L,
                1,
                PERIOD_START_DATE,
                REFERENCE_AT
        );
        SubscriptionSetting setting = SubscriptionSetting.createFirstAwaitingConfirmation(
                1L,
                10L,
                PERIOD_START_DATE
        );

        period.markScheduled();
        setting.activate(REFERENCE_AT.plusMinutes(1));

        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        assertThat(setting.getStatus()).isEqualTo(SubscriptionSettingStatus.ACTIVE);
        assertThat(setting.getConfirmedAt()).isEqualTo(REFERENCE_AT.plusMinutes(1));
    }

    @Test
    void 요일별_인원수는_1명부터_6명까지만_허용한다() {
        assertThatThrownBy(() -> SubscriptionDeliveryCondition.create(
                1L,
                DeliveryWeekday.MONDAY,
                7,
                1L,
                DeliveryTimeSlot.TIME_1100_1300
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 요일별_배송_조건을_확정된_코드로_생성한다() {
        SubscriptionDeliveryCondition condition = SubscriptionDeliveryCondition.create(
                1L,
                DeliveryWeekday.SATURDAY,
                6,
                2L,
                DeliveryTimeSlot.TIME_1700_1900
        );

        assertThat(condition.getDeliveryWeekday()).isEqualTo(DeliveryWeekday.SATURDAY);
        assertThat(condition.getMealQuantity()).isEqualTo(6);
        assertThat(condition.getDeliveryTimeSlot()).isEqualTo(DeliveryTimeSlot.TIME_1700_1900);
    }

    @Test
    void 후속_설정은_처리_기준_시각을_필수로_저장한다() {
        SubscriptionSetting setting = SubscriptionSetting.createAwaitingConfirmation(
                1L,
                10L,
                2,
                REFERENCE_AT,
                PERIOD_START_DATE
        );

        assertThat(setting.getSettingSequence()).isEqualTo(2);
        assertThat(setting.getProcessingReferenceAt()).isEqualTo(REFERENCE_AT);
    }
}
