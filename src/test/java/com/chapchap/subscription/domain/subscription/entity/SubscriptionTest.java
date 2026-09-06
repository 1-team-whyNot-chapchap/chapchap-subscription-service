package com.chapchap.subscription.domain.subscription.entity;

import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    @Test
    void 새_구독은_확정_대기로_생성한다() {
        Subscription subscription = Subscription.create(1L);

        assertThat(PublicIdFormat.isUuidV4(subscription.getPublicId())).isTrue();
        assertThat(subscription.getUserId()).isEqualTo(1L);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
        assertThat(subscription.isFirstSubscriptionDiscountUsed()).isFalse();
    }

    @Test
    void 첫_결제_성공으로_시작_예정_상태가_된다() {
        Subscription subscription = Subscription.create(1L);

        SubscriptionStatus previous = subscription.markScheduled();

        assertThat(previous).isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
    }

    @Test
    void 결제_실패_구독은_확정_대기로_재신청할_수_있다() {
        Subscription subscription = Subscription.create(1L);
        subscription.markPaymentFailed();

        subscription.prepareReapplication();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void 문서에_없는_상태_전이는_거부한다() {
        Subscription subscription = Subscription.create(1L);

        assertThatThrownBy(subscription::prepareReapplication)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 첫_구독_할인은_결제_성공_후_사용_처리한다() {
        Subscription subscription = Subscription.create(1L);

        subscription.markScheduled();
        subscription.markFirstSubscriptionDiscountUsed();

        assertThat(subscription.isFirstSubscriptionDiscountUsed()).isTrue();
    }
}
