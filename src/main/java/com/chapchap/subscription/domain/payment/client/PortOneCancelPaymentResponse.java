package com.chapchap.subscription.domain.payment.client;

/** PortOne V2 결제 취소 응답 중 확정 판정에 필요한 값만 수신한다. */
public record PortOneCancelPaymentResponse(Cancellation cancellation) {
    public record Cancellation(String status, String id, String pgCancellationId) {
    }
}
