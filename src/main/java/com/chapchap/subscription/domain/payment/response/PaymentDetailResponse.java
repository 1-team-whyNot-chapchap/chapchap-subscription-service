package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 결제 거래 당시 금액·기간과 외부 처리 시도의 비민감 정보를 제공한다. */
public record PaymentDetailResponse(
    String paymentId,
    PaymentTransactionType paymentType,
    PaymentTransactionStatus status,
    Long amount,
    LocalDateTime occurredAt,
    Long originalPaymentAmount,
    Long cumulativeCancelAmount,
    Long cancelableAmount,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    List<PaymentAttemptResponse> attempts
) {
    public PaymentDetailResponse {
        attempts = List.copyOf(attempts);
    }

    public record PaymentAttemptResponse(
        Integer attemptSequence,
        Long requestedAmount,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        PaymentAttemptResult result,
        String cardCompany,
        String maskedCardNumber
    ) {
    }
}
