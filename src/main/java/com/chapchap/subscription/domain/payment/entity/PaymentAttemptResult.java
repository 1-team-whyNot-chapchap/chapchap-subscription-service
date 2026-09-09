package com.chapchap.subscription.domain.payment.entity;

/** 외부 결제 요청 한 번의 응답 결과다. */
public enum PaymentAttemptResult {
    /** 외부 결제 또는 취소가 성공했다. */
    SUCCESS,
    /** 외부 결제 또는 취소가 실패했다. */
    FAILURE
}
