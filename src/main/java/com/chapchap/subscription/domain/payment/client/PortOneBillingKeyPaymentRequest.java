package com.chapchap.subscription.domain.payment.client;

/**
 * PortOne V2 빌링키 결제 API에 전달하는 요청 Body다.
 * 외부 API 형식은 이 DTO 안에만 두어 내부 결제 업무 계약과 분리한다.
 *
 * @param billingKey PortOne에 전달할 빌링키
 * @param orderName PortOne 결제 내역에 표시할 주문명
 * @param amount PortOne 금액 객체
 * @param currency 결제 통화 코드
 */
public record PortOneBillingKeyPaymentRequest(
    String billingKey,
    String orderName,
    Amount amount,
    String currency
) {
    /**
     * PortOne이 요구하는 결제금액 객체다.
     *
     * @param total PortOne에 요청할 총 결제금액
     */
    public record Amount(long total) {
    }

    /**
     * Provider 중립 자동결제 요청을 PortOne V2 요청 형식으로 변환한다.
     *
     * @param request 내부 공통 자동결제 요청
     * @return PortOne V2 빌링키 결제 요청 Body
     */
    public static PortOneBillingKeyPaymentRequest from(AutomaticPaymentRequest request) {
        return new PortOneBillingKeyPaymentRequest(
            request.externalMethodReference(),
            request.orderName(),
            new Amount(request.totalAmount()),
            request.currency()
        );
    }

    /** 빌링키가 로그에 노출되지 않도록 민감 필드를 마스킹한다. */
    @Override
    public String toString() {
        return "PortOneBillingKeyPaymentRequest[billingKey=***"
            + ", orderName=" + orderName
            + ", amount=" + amount
            + ", currency=" + currency + "]";
    }
}
