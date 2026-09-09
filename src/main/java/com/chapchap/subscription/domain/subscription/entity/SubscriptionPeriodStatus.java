package com.chapchap.subscription.domain.subscription.entity;

// 구독 이용 기간 상태
public enum SubscriptionPeriodStatus {
    AWAITING_CONFIRMATION,
    SCHEDULED,
    IN_PROGRESS,
    ENDED,
    CANCELED_BEFORE_START,
    PAYMENT_FAILED
}
