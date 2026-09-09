package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionOngoingStatusReaderTest {

    private static final Long USER_ID = 1L;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionOngoingStatusReader ongoingStatusReader;

    @ParameterizedTest
    @EnumSource(
        value = SubscriptionStatus.class,
        names = {
            "AWAITING_CONFIRMATION",
            "SCHEDULED",
            "IN_PROGRESS",
            "CANCELLATION_SCHEDULED"
        }
    )
    void 진행_중_구독_상태이면_true를_반환한다(SubscriptionStatus status) {
        mockSubscriptionStatus(status);

        assertThat(ongoingStatusReader.existsOngoingSubscription(USER_ID)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = SubscriptionStatus.class,
        names = {
            "PAYMENT_FAILED",
            "CANCELED_BEFORE_START",
            "ENDED"
        }
    )
    void 종료되었거나_다시_시작할_수_있는_구독_상태이면_false를_반환한다(SubscriptionStatus status) {
        mockSubscriptionStatus(status);

        assertThat(ongoingStatusReader.existsOngoingSubscription(USER_ID)).isFalse();
    }

    @Test
    void 구독이_없으면_false를_반환한다() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(ongoingStatusReader.existsOngoingSubscription(USER_ID)).isFalse();
    }

    private void mockSubscriptionStatus(SubscriptionStatus status) {
        Subscription subscription = mock(Subscription.class);
        when(subscription.getStatus()).thenReturn(status);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
    }
}
