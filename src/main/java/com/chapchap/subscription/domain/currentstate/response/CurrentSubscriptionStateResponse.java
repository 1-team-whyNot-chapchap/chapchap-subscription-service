package com.chapchap.subscription.domain.currentstate.response;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;

/** Customer-AI에 제공하는 현재 구독의 최소 업무 사실이다. */
public record CurrentSubscriptionStateResponse(
    SubscriptionStatus status
) {
}
