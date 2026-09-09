package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;

import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객 구독의 환불 업무 목록 응답이다. */
public record RefundListResponse(List<RefundItemResponse> refunds) {
    public RefundListResponse {
        refunds = List.copyOf(refunds);
    }

    public record RefundItemResponse(
        String refundId,
        RefundType refundType,
        RefundStatus status,
        Long requestedAmount,
        Long refundedAmount,
        Long unprocessedAmount,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
    ) {
    }
}
