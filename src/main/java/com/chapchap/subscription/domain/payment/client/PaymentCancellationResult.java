package com.chapchap.subscription.domain.payment.client;

/** 외부 원 결제 취소의 명시적으로 확정된 결과다. */
public record PaymentCancellationResult(
    PaymentCancellationStatus status,
    String externalPaymentId,
    String externalCancellationId,
    String externalResultCode,
    String failureReason
) {
    public static PaymentCancellationResult succeeded(String paymentId, String cancellationId) {
        return new PaymentCancellationResult(
            PaymentCancellationStatus.SUCCEEDED, paymentId, cancellationId, "SUCCEEDED", null
        );
    }

    public static PaymentCancellationResult declined(String paymentId, String code) {
        return new PaymentCancellationResult(
            PaymentCancellationStatus.DECLINED, paymentId, null,
            code == null || code.isBlank() ? "CANCELLATION_FAILED" : code,
            "외부 원 결제 취소가 거절되었습니다."
        );
    }

    public static PaymentCancellationResult configurationFailed(String paymentId, String code) {
        return new PaymentCancellationResult(
            PaymentCancellationStatus.PROVIDER_CONFIGURATION_FAILED, paymentId, null,
            code == null || code.isBlank() ? "PROVIDER_CONFIGURATION_FAILED" : code,
            "외부 결제 연동 설정 오류가 발생했습니다."
        );
    }

    public boolean isSucceeded() {
        return status == PaymentCancellationStatus.SUCCEEDED;
    }
}
