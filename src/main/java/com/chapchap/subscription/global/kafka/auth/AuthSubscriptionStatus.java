package com.chapchap.subscription.global.kafka.auth;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;

/** Auth가 사용하는 두 가지 구독 가능 상태다. */
public enum AuthSubscriptionStatus {
    ACTIVE, INACTIVE;

    public static AuthSubscriptionStatus from(SubscriptionStatus status) {
        return switch (status) {
            case SCHEDULED, IN_PROGRESS, CANCELLATION_SCHEDULED -> ACTIVE;
            case AWAITING_CONFIRMATION, PAYMENT_FAILED, CANCELED_BEFORE_START, ENDED -> INACTIVE;
        };
    }
}
