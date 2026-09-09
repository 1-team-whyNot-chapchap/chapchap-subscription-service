package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.FirstOrderPreparationResult;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;

import java.time.LocalDate;

/**
 * prepare 트랜잭션에서 확정한 구독·주문·결제 정보를 외부 PG 실행 단계로 전달한다.
 * {@code paymentRequired=false}이면 기존 PROCESSING 요청이므로 PG를 다시 호출하지 않는다.
 */
public record PreparedFirstSubscription(
    Long subscriptionId,
    String subscriptionPublicId,
    Long subscriptionPeriodId,
    Long subscriptionSettingId,
    SubscriptionStatus status,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    Long paymentTransactionId,
    FirstOrderPreparationResult orderResult,
    boolean paymentRequired
) {
    /** 기존 처리 중인 첫 결제의 식별자와 실제 이용 기간으로 멱등 응답 결과를 생성한다. */
    public static PreparedFirstSubscription processing(
        Long subscriptionId,
        String subscriptionPublicId,
        Long subscriptionPeriodId,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Long paymentTransactionId
    ) {
        return new PreparedFirstSubscription(
            subscriptionId,
            subscriptionPublicId,
            subscriptionPeriodId,
            null,
            SubscriptionStatus.AWAITING_CONFIRMATION,
            periodStartDate,
            periodEndDate,
            paymentTransactionId,
            null,
            false
        );
    }
}
