package com.chapchap.subscription.domain.payment.client;

/**
 * PortOne V2 빌링키 결제 응답 중 내부 처리에 필요한 결제 상태와 식별정보만 받는 DTO다.
 * 외부 응답 원문 전체를 저장하거나 고객 응답에 그대로 노출하지 않는다.
 *
 * @param payment PortOne가 반환한 결제 결과
 */
public record PortOneBillingKeyPaymentResponse(Payment payment) {
    /**
     * 성공과 실패에 공통으로 존재할 수 있는 PortOne 결제 결과다.
     *
     * @param status PortOne 결제 상태
     * @param id PortOne 결제 건 식별자
     * @param transactionId PortOne 또는 PG가 반환한 거래 처리 식별정보
     * @param failure 실패한 결제에 포함되는 상세 정보
     */
    public record Payment(
        String status,
        String id,
        String transactionId,
        Failure failure
    ) {
    }

    /**
     * 실패한 PortOne 결제에 포함되는 진단 정보다.
     *
     * @param reason 외부 결제 실패 사유
     * @param pgCode PG 결과 코드
     * @param pgMessage PG 결과 메시지
     */
    public record Failure(
        String reason,
        String pgCode,
        String pgMessage
    ) {
    }
}
