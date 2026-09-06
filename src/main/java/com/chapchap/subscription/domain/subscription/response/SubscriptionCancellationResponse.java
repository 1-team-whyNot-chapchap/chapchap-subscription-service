package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationType;
import java.time.LocalDateTime;

public record SubscriptionCancellationResponse(
    SubscriptionCancellationType cancellationType,
    SubscriptionStatus subscriptionStatus,
    SubscriptionPeriodStatus periodStatus,
    LocalDateTime cancellationRequestedAt,
    RefundSummary refund
) {
    public record RefundSummary(
        String refundId,
        RefundStatus status,
        Long requestedAmount,
        Long refundedAmount,
        Long unprocessedAmount
    ) {
    }
}
