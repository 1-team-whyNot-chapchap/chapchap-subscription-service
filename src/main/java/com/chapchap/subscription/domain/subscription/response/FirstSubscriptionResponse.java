package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;

import java.time.LocalDate;

/** 첫 결제 성공 또는 기존 처리 중 재요청에 반환하는 구독·이용 기간 정보다. */
public record FirstSubscriptionResponse(
    String subscriptionId,
    SubscriptionStatus subscriptionStatus,
    LocalDate periodStartDate,
    LocalDate periodEndDate
) {
}
