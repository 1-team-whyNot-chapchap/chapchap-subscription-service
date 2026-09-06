package com.chapchap.subscription.domain.payment.client;

/** 외부 제공자의 원 결제 취소 API 경계다. */
public interface PaymentCancellationClient {
    PaymentCancellationResult cancel(PaymentCancellationRequest request);
}
