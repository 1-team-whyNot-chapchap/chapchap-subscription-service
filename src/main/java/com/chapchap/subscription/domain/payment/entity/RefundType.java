package com.chapchap.subscription.domain.payment.entity;

/** 환불이 발생한 구독 업무를 구분한다. */
public enum RefundType {
    SETTING_CHANGE_REDUCTION,
    CANCELLATION_BEFORE_START,
    NEXT_PERIOD_FULL_CANCELLATION,
    DELIVERY_PARTIAL_CANCELLATION
}
