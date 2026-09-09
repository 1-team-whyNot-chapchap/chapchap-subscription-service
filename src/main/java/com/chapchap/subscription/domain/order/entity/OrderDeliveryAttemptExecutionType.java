package com.chapchap.subscription.domain.order.entity;

/** 주문 Kafka 전달의 예약 실행 구분이다. */
public enum OrderDeliveryAttemptExecutionType {
    INITIAL_1500,
    RETRY_1600
}
