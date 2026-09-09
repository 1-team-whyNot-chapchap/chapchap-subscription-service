package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;

/** 배송 건 환불의 현재 상태와 다음 외부 취소 대상을 전달한다. */
public record PreparedDeliveryRefund(
    Long refundId,
    RefundStatus status,
    Long cancellationTransactionId,
    boolean duplicate
) {
}
