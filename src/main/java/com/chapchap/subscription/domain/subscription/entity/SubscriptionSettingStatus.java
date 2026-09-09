package com.chapchap.subscription.domain.subscription.entity;

// 구독 이용 기간 상태
public enum SubscriptionSettingStatus {
    AWAITING_CONFIRMATION,
    CHANGE_PENDING,
    ACTIVE,
    PAYMENT_FAILED,
    CHANGE_NOT_APPLIED,
    ENDED
}
