package com.chapchap.subscription.domain.order.entity;

/** 주문을 Delivery Kafka Topic에 저장한 결과 상태다. */
public enum OrderKafkaDeliveryStatus {
    NOT_SENT,
    FAILED,
    COMPLETED,
    FINAL_FAILED
}
