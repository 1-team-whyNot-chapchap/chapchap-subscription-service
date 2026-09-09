package com.chapchap.subscription.domain.order.entity;

/** 결제·설정 변경·시작 취소 결과에 따른 주문의 업무 상태다. */
public enum OrderStatus {
    AWAITING_CONFIRMATION,
    CHANGE_PENDING,
    ACTIVE,
    INACTIVE,
    PAYMENT_FAILED,
    CHANGE_NOT_APPLIED,
    CANCELED_BEFORE_START
}
