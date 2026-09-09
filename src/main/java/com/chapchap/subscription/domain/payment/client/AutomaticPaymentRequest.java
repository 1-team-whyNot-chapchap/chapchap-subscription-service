package com.chapchap.subscription.domain.payment.client;

/**
 * 특정 결제 제공자에 종속되지 않은 자동결제 요청 값이다.
 *
 * <p>{@code externalMethodReference}에는 외부 요청 직전에 복호화한 결제수단 참조값이 들어가므로
 * 저장하거나 로그에 출력하지 않고 Provider Client 호출 범위에서만 사용한다.</p>
 *
 * @param externalPaymentId 외부 결제 제공자에 전달할 결제 건 식별자
 * @param idempotencyKey 같은 외부 요청의 중복 처리를 막기 위한 멱등성 키
 * @param externalMethodReference 외부 요청 직전에 복호화한 결제수단 참조값
 * @param orderName 외부 결제 내역에 표시할 주문명
 * @param totalAmount 결제를 요청할 총금액
 * @param currency 결제 통화 코드
 */
public record AutomaticPaymentRequest(
    String externalPaymentId,
    String idempotencyKey,
    String externalMethodReference,
    String orderName,
    long totalAmount,
    String currency
) {
    private static final String IDEMPOTENCY_KEY_PATTERN = "[A-Za-z0-9_-]{16,256}";

    /**
     * 필수 문자열과 양수 금액을 검증한다.
     *
     * @throws IllegalArgumentException 필수 문자열이 비어 있거나 결제금액이 0 이하인 경우
     */
    public AutomaticPaymentRequest {
        requireText(externalPaymentId, "externalPaymentId");
        requireText(idempotencyKey, "idempotencyKey");
        if (!idempotencyKey.matches(IDEMPOTENCY_KEY_PATTERN)) {
            throw new IllegalArgumentException(
                "idempotencyKey must be 16 to 256 ASCII letters, digits, hyphens, or underscores"
            );
        }
        requireText(externalMethodReference, "externalMethodReference");
        requireText(orderName, "orderName");
        requireText(currency, "currency");
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /** 결제수단 참조값이 로그에 노출되지 않도록 민감 필드를 마스킹한다. */
    @Override
    public String toString() {
        return "AutomaticPaymentRequest[externalPaymentId=" + externalPaymentId
            + ", idempotencyKey=***"
            + ", externalMethodReference=***"
            + ", orderName=" + orderName
            + ", totalAmount=" + totalAmount
            + ", currency=" + currency + "]";
    }
}
