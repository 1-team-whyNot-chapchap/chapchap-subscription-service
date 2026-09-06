package com.chapchap.subscription.domain.payment.client;

/** PortOne V2 결제 취소 요청 Body다. */
public record PortOneCancelPaymentRequest(
    long amount,
    long currentCancellableAmount,
    String reason,
    String requester
) {
    public static PortOneCancelPaymentRequest from(PaymentCancellationRequest request) {
        return new PortOneCancelPaymentRequest(
            request.amount(), request.currentCancellableAmount(), request.reason(), "CUSTOMER"
        );
    }
}
