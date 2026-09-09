package com.chapchap.subscription.domain.payment.service.result;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;

/**
 * 첫 결제 응답을 로컬 데이터에 확정한 결과다.
 *
 * @param paymentTransactionId 확정한 결제 거래 식별자
 * @param transactionStatus 확정 후 결제 거래 상태
 * @param attemptResult 저장한 결제 시도 결과
 * @param allocationCount 성공 결제에 생성한 주문별 배분 수
 * @param providerStatus 저장이 끝난 뒤 상위 통합 계층이 고객 응답을 결정할 Provider 결과
 */
public record CompletedFirstPayment(
    Long paymentTransactionId,
    PaymentTransactionStatus transactionStatus,
    PaymentAttemptResult attemptResult,
    int allocationCount,
    AutomaticPaymentStatus providerStatus
) {
}
