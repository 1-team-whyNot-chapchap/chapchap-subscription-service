package com.chapchap.subscription.domain.payment.service;

/** Delivery 환불 Event가 확정 계약이나 Subscription 데이터 연결을 충족하지 못한 경우다. */
public class DeliveryRefundContractException extends RuntimeException {
    public DeliveryRefundContractException(String message) {
        super(message);
    }
}
