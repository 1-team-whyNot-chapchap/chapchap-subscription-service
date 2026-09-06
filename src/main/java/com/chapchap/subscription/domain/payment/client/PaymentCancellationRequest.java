package com.chapchap.subscription.domain.payment.client;

/** 외부 원 결제 취소 요청에 필요한 최소 값이다. */
public record PaymentCancellationRequest(
    String externalPaymentId,
    String idempotencyKey,
    long amount,
    long currentCancellableAmount,
    String reason
) {
    public PaymentCancellationRequest {
        requireText(externalPaymentId, "externalPaymentId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        if (amount <= 0 || currentCancellableAmount < amount) {
            throw new IllegalArgumentException("Cancellation amount exceeds current cancellable amount");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
