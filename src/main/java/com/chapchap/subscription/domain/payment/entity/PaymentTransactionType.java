package com.chapchap.subscription.domain.payment.entity;

/** 결제 거래가 발생한 구독 업무의 종류다. */
public enum PaymentTransactionType {
    /** 첫 28일 이용 기간의 선결제다. */
    FIRST_SUBSCRIPTION_PAYMENT,
    /** 다음 28일 이용 기간을 위한 정기결제다. */
    REGULAR_PAYMENT,
    /** 구독 설정 변경으로 금액이 증가하여 발생한 추가 결제다. */
    SETTING_CHANGE_PAYMENT,
    /** 구독 설정 변경으로 금액이 감소하여 수행하는 원 결제 부분 취소다. */
    SETTING_CHANGE_PARTIAL_CANCELLATION,
    /** 첫 이용 시작 전에 구독을 취소하여 수행하는 원 결제 취소다. */
    CANCELLATION_BEFORE_START,
    /** 결제된 다음 이용 기간 시작 전에 해지하여 수행하는 전액 취소다. */
    NEXT_PERIOD_FULL_CANCELLATION,
    /** 환불 대상으로 확정된 배송 건에 대해 수행하는 부분 취소다. */
    DELIVERY_PARTIAL_CANCELLATION
}
