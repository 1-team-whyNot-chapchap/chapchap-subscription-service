package com.chapchap.subscription.domain.payment.entity;

/** 여러 원 결제 취소로 구성되는 환불 업무 전체의 현재 결과다. */
public enum RefundStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVIEW_REQUIRED
}
