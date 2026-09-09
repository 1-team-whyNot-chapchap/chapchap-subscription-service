package com.chapchap.subscription.domain.currentstate.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;

import java.time.OffsetDateTime;

/** Customer-AI에 제공하는 최근 환불의 최소 업무 사실이다. */
public record CurrentRefundStateResponse(
    RefundStatus status,
    RefundType refundType,
    Long requestedAmount,
    Long refundedAmount,
    Long unprocessedAmount,
    OffsetDateTime requestedAt,
    OffsetDateTime completedAt
) {
}
