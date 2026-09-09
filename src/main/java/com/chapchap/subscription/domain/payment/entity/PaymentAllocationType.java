package com.chapchap.subscription.domain.payment.entity;

/** 성공한 원 결제의 업무 종류를 주문별 금액 배분에 보존하는 구분이다. */
public enum PaymentAllocationType {
    /** 첫 구독 결제금액의 주문별 배분이다. */
    FIRST_SUBSCRIPTION_PAYMENT,
    /** 정기결제 금액의 주문별 배분이다. */
    REGULAR_PAYMENT,
    /** 설정 변경으로 발생한 추가 결제금액의 주문별 배분이다. */
    SETTING_CHANGE_PAYMENT
}
