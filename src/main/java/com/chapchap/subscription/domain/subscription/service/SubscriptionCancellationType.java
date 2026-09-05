package com.chapchap.subscription.domain.subscription.service;

/** SUB-FN-007에서 서버가 현재 상태로 결정하는 해지·취소 유형이다. */
public enum SubscriptionCancellationType {
    REGULAR_CANCELLATION,
    CANCELLATION_BEFORE_START,
    NEXT_PERIOD_FULL_CANCELLATION,
    REGULAR_PAYMENT_RETRY_STOPPED
}
