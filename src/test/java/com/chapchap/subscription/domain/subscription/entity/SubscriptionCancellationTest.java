package com.chapchap.subscription.domain.subscription.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionCancellationTest {
    @Test
    void 시작예정_구독은_시작취소로_전환된다() {
        Subscription subscription = Subscription.create(1L);
        subscription.markScheduled();
        subscription.cancelBeforeStart(LocalDateTime.of(2026, 9, 8, 12, 0));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED_BEFORE_START);
        assertThat(subscription.getCancellationRequestedAt()).isNull();
    }

    @Test
    void 이용중_구독은_해지예정으로_전환된다() {
        Subscription subscription = Subscription.create(1L);
        subscription.markScheduled(); subscription.startFirstPeriod();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 8, 12, 0);
        subscription.scheduleCancellation(requestedAt);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_SCHEDULED);
        assertThat(subscription.getCancellationRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void 해지예정_구독은_종료시_해지신청시각을_비운다() {
        Subscription subscription = Subscription.create(1L);
        subscription.markScheduled(); subscription.startFirstPeriod();
        subscription.scheduleCancellation(LocalDateTime.of(2026, 9, 8, 12, 0));

        subscription.end();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(subscription.getCancellationRequestedAt()).isNull();
    }
}
