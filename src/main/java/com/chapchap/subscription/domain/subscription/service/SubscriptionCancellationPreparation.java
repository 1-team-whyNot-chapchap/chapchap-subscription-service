package com.chapchap.subscription.domain.subscription.service;

import java.time.LocalDateTime;
import java.util.List;

/** 결제·환불 호출 전에 확정한 취소 대상 기간·주문과 처리 기준 시각이다. */
public record SubscriptionCancellationPreparation(
    SubscriptionCancellationType cancellationType,
    Long subscriptionId,
    Long targetPeriodId,
    List<Long> targetOrderIds,
    LocalDateTime referenceAt
) {
    public SubscriptionCancellationPreparation {
        targetOrderIds = List.copyOf(targetOrderIds);
    }
}
