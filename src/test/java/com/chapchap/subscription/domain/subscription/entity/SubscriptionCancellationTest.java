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
    }

    @Test
    void 이용중_구독은_해지예정으로_전환된다() {
        Subscription subscription = Subscription.create(1L);
        subscription.markScheduled(); subscription.startFirstPeriod();
        subscription.scheduleCancellation(LocalDateTime.of(2026, 9, 8, 12, 0));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_SCHEDULED);
    }
}
