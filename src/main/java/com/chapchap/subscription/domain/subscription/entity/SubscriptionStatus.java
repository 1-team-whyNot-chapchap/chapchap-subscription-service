package com.chapchap.subscription.domain.subscription.entity;

// 구독 이용 기간 상태
public enum SubscriptionStatus {
    AWAITING_CONFIRMATION,
    SCHEDULED,
    IN_PROGRESS,
    CANCELLATION_SCHEDULED,
    PAYMENT_FAILED,
    CANCELED_BEFORE_START,
    ENDED
}
