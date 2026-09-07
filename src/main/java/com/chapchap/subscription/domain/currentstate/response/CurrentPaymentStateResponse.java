package com.chapchap.subscription.domain.currentstate.response;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;

import java.time.OffsetDateTime;

/** Customer-AI에 제공하는 현재 결제의 최소 업무 사실이다. */
public record CurrentPaymentStateResponse(
    PaymentTransactionStatus status,
    PaymentTransactionType paymentType,
    Long amount,
    OffsetDateTime occurredAt
) {
}
