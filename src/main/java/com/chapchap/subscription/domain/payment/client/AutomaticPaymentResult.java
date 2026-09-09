package com.chapchap.subscription.domain.payment.client;

/**
 * 외부 결제 제공자의 응답을 내부 결제 처리에서 공통으로 사용할 수 있게 변환한 결과다.
 *
 * @param status 외부 결제에서 명시적으로 확정된 성공 또는 실패 상태
 * @param externalPaymentId 외부 결제 건 식별자
 * @param externalTransactionRef 성공한 외부 거래의 처리 식별정보
 * @param externalResultCode 외부 제공자가 반환한 결과 코드
 * @param failureReason 외부 결제 실패 사유
 */
public record AutomaticPaymentResult(
    AutomaticPaymentStatus status,
    String externalPaymentId,
    String externalTransactionRef,
    String externalResultCode,
    String failureReason
) {
    /** 성공·거절별 필드 조합을 검증한다. */
    public AutomaticPaymentResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        requireText(externalPaymentId, "externalPaymentId");
        requireText(externalResultCode, "externalResultCode");
        if (status == AutomaticPaymentStatus.PAID) {
            requireText(externalTransactionRef, "externalTransactionRef");
            if (failureReason != null) {
                throw new IllegalArgumentException("A paid payment must not have a failure reason");
            }
        } else {
            requireText(failureReason, "failureReason");
            if (externalTransactionRef != null) {
                throw new IllegalArgumentException("A failed payment must not have a transaction reference");
            }
        }
    }

    /**
     * 외부 결제 성공 결과를 생성하며 외부 거래 식별정보를 필수로 보존한다.
     *
     * @param externalPaymentId 외부 결제 건 식별자
     * @param externalTransactionRef 성공한 외부 거래의 처리 식별정보
     * @param externalResultCode 외부 제공자가 반환한 결과 코드
     * @return 성공 상태의 공통 자동결제 결과
     */
    public static AutomaticPaymentResult success(
        String externalPaymentId,
        String externalTransactionRef,
        String externalResultCode
    ) {
        requireText(externalPaymentId, "externalPaymentId");
        requireText(externalTransactionRef, "externalTransactionRef");
        return new AutomaticPaymentResult(
            AutomaticPaymentStatus.PAID,
            externalPaymentId,
            externalTransactionRef,
            externalResultCode,
            null
        );
    }

    /**
     * 외부 결제 실패 결과를 생성하며 성공 거래 식별정보 대신 정제된 실패 사유를 보존한다.
     *
     * @param externalPaymentId 외부 결제 건 식별자
     * @param externalResultCode 외부 제공자가 반환한 결과 코드
     * @param failureReason Secret과 결제수단 참조값을 제거한 실패 사유
     * @return 실패 상태의 공통 자동결제 결과
     */
    public static AutomaticPaymentResult declined(
        String externalPaymentId,
        String externalResultCode,
        String failureReason
    ) {
        requireText(failureReason, "failureReason");
        return new AutomaticPaymentResult(
            AutomaticPaymentStatus.DECLINED,
            externalPaymentId,
            null,
            externalResultCode,
            failureReason
        );
    }

    /**
     * Provider 인증·권한·채널 또는 서버 요청 설정 오류를 명시적 실패 결과로 생성한다.
     * 이 결과를 반환해야 실패 이력을 먼저 저장한 후 상위 통합 계층에서 PAYMENT_002를 응답할 수 있다.
     */
    public static AutomaticPaymentResult providerConfigurationFailed(
        String externalPaymentId,
        String externalResultCode,
        String failureReason
    ) {
        requireText(failureReason, "failureReason");
        return new AutomaticPaymentResult(
            AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED,
            externalPaymentId,
            null,
            externalResultCode,
            failureReason
        );
    }

    /** 외부 결제가 명시적으로 성공했는지 확인한다. */
    public boolean isPaid() {
        return status == AutomaticPaymentStatus.PAID;
    }

    /** 외부 실패 사유가 일반 로그에 원문으로 노출되지 않도록 문자열 표현에서 제외한다. */
    @Override
    public String toString() {
        return "AutomaticPaymentResult[status=" + status
            + ", externalPaymentId=" + externalPaymentId
            + ", externalTransactionRef=" + externalTransactionRef
            + ", externalResultCode=" + externalResultCode
            + ", failureReason=" + (failureReason == null ? null : "***") + ']';
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
