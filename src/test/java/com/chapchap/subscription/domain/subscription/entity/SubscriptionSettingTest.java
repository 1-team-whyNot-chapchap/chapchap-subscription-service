package com.chapchap.subscription.domain.subscription.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionSettingTest {

    @Test
    void 변경대기_설정은_확정되면_유효_설정이_된다() {
        SubscriptionSetting setting = SubscriptionSetting.createChangePending(
            1L, 2L, 2, LocalDateTime.of(2026, 9, 8, 13, 0), LocalDate.of(2026, 9, 9)
        );

        setting.activateChange(LocalDateTime.of(2026, 9, 8, 13, 1));

        assertThat(setting.getStatus()).isEqualTo(SubscriptionSettingStatus.ACTIVE);
        assertThat(setting.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 13, 1));
    }

    @Test
    void 변경대기_설정은_실패하면_변경미적용으로_기록한다() {
        SubscriptionSetting setting = SubscriptionSetting.createChangePending(
            1L, 2L, 2, LocalDateTime.of(2026, 9, 8, 13, 0), LocalDate.of(2026, 9, 9)
        );

        setting.markChangeNotApplied(LocalDateTime.of(2026, 9, 8, 13, 1));

        assertThat(setting.getStatus()).isEqualTo(SubscriptionSettingStatus.CHANGE_NOT_APPLIED);
    }

    @Test
    void 유효_설정은_새_설정의_적용일부터_종료할_수_있다() {
        SubscriptionSetting setting = SubscriptionSetting.createFirstAwaitingConfirmation(1L, 2L, LocalDate.of(2026, 9, 1));
        setting.activate(LocalDateTime.of(2026, 9, 1, 0, 0));

        setting.closeAt(LocalDate.of(2026, 9, 9));

        assertThat(setting.getEffectiveEndExclusiveDate()).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void 첫_설정은_변경대기_상태로_만들수_없다() {
        assertThatThrownBy(() -> SubscriptionSetting.createChangePending(
            1L, 2L, 1, null, LocalDate.of(2026, 9, 9)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
