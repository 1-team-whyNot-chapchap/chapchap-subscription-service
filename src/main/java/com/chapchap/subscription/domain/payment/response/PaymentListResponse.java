package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;

import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객의 결제·원 결제 취소 거래 목록 응답이다. */
public record PaymentListResponse(List<PaymentItemResponse> payments) {
    public PaymentListResponse {
        payments = List.copyOf(payments);
    }

    public record PaymentItemResponse(
        String paymentId,
        PaymentTransactionType paymentType,
        PaymentTransactionStatus status,
        Long amount,
        LocalDateTime occurredAt
    ) {
    }
}
