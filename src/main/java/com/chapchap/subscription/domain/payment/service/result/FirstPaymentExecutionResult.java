package com.chapchap.subscription.domain.payment.service.result;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;

import java.time.LocalDateTime;

/**
 * 외부 첫 결제 응답과 해당 요청에 실제 사용한 내부 정보를 결과 확정 단계로 전달한다.
 *
 * @param paymentTransactionId 응답을 확정할 결제 거래 식별자
 * @param paymentMethodId 외부 요청에 실제 사용한 결제수단 식별자
 * @param providerCode 요청을 처리한 외부 결제 제공자
 * @param idempotencyKey 외부 요청에 사용한 멱등성 키
 * @param requestedAmount 외부 제공자에 요청한 금액
 * @param requestedAt 외부 요청 전송 시각
 * @param respondedAt 외부 응답 수신 시각
 * @param providerResult 외부 응답을 내부 성공·실패로 변환한 결과
 */
public record FirstPaymentExecutionResult(
    Long paymentTransactionId,
    Long paymentMethodId,
    PaymentProviderCode providerCode,
    String idempotencyKey,
    Long requestedAmount,
    LocalDateTime requestedAt,
    LocalDateTime respondedAt,
    AutomaticPaymentResult providerResult
) {
    /** 결과 확정에 필요한 식별자·금액·시각·Provider 결과를 검증한다. */
    public FirstPaymentExecutionResult {
        requirePositive(paymentTransactionId, "paymentTransactionId");
        requirePositive(paymentMethodId, "paymentMethodId");
        if (providerCode == null) {
            throw new IllegalArgumentException("providerCode must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        requirePositive(requestedAmount, "requestedAmount");
        if (requestedAt == null || respondedAt == null) {
            throw new IllegalArgumentException("request and response times must not be null");
        }
        if (respondedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("respondedAt must not be before requestedAt");
        }
        if (providerResult == null) {
            throw new IllegalArgumentException("providerResult must not be null");
        }
    }

    /** 외부 요청 멱등성 키가 일반 로그에 노출되지 않도록 문자열 표현에서 마스킹한다. */
    @Override
    public String toString() {
        return "FirstPaymentExecutionResult[paymentTransactionId=" + paymentTransactionId
            + ", paymentMethodId=" + paymentMethodId
            + ", providerCode=" + providerCode
            + ", idempotencyKey=***"
            + ", requestedAmount=" + requestedAmount
            + ", requestedAt=" + requestedAt
            + ", respondedAt=" + respondedAt
            + ", providerResult=" + providerResult + ']';
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
