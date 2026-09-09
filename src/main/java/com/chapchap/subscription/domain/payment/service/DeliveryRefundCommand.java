package com.chapchap.subscription.domain.payment.service;

/** 검증을 마친 Delivery 환불 확정 사실의 업무 입력이다. */
public record DeliveryRefundCommand(
    String deliveryId,
    String orderId,
    Long userId
) {
}
