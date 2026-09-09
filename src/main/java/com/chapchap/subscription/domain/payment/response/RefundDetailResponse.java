package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;

import java.time.LocalDateTime;
import java.util.List;

/** 환불 집계와 연결된 원 결제 취소 거래를 제공하는 환불 상세 응답이다. */
public record RefundDetailResponse(
    String refundId,
    RefundType refundType,
    RefundStatus status,
    Long requestedAmount,
    Long refundedAmount,
    Long unprocessedAmount,
    LocalDateTime requestedAt,
    LocalDateTime completedAt,
    List<CancellationResponse> cancellations
) {
    public RefundDetailResponse {
        cancellations = List.copyOf(cancellations);
    }

    public record CancellationResponse(
        String paymentId,
        String originalPaymentId,
        PaymentTransactionStatus status,
        Long amount,
        LocalDateTime occurredAt
    ) {
    }
}
