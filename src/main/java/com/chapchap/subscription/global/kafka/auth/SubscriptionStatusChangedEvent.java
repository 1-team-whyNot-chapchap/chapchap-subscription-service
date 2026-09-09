package com.chapchap.subscription.global.kafka.auth;

import java.time.OffsetDateTime;

/** Auth Projection 갱신에 필요한 최소 구독 상태 Event 계약이다. */
public record SubscriptionStatusChangedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "SUBSCRIPTION_STATUS_CHANGED";

    public record Data(AuthSubscriptionStatus subscriptionStatus, int subscriptionVersion) {}
}
