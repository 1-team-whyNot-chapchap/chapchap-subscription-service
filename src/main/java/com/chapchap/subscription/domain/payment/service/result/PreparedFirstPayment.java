package com.chapchap.subscription.domain.payment.service.result;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;

/**
 * 첫 결제 준비 결과로 새 거래 생성 여부와 현재 거래 상태만 제공한다.
 *
 * @param paymentTransactionId 결제 거래의 내부 식별자
 * @param paymentPublicId 외부 결제 식별에도 사용하는 결제 공개 식별자
 * @param status 준비된 거래의 현재 상태
 * @param newlyCreated 이번 요청에서 거래를 새로 생성했는지 여부
 */
public record PreparedFirstPayment(
    Long paymentTransactionId,
    String paymentPublicId,
    PaymentTransactionStatus status,
    boolean newlyCreated
) {
    /** 저장된 결제 거래를 Subscription 오케스트레이터가 사용할 최소 결과로 변환한다. */
    public static PreparedFirstPayment from(PaymentTransaction transaction, boolean newlyCreated) {
        return new PreparedFirstPayment(
            transaction.getId(),
            transaction.getPublicId(),
            transaction.getStatus(),
            newlyCreated
        );
    }
}
