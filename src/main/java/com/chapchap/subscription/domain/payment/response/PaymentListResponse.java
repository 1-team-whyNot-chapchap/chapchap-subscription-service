package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객의 결제·원 결제 취소 거래 목록 응답이다. */
@Schema(description = "인증 고객의 결제 및 원 결제 취소 거래 목록")
public record PaymentListResponse(
    @Schema(description = "결제 거래 목록")
    List<PaymentItemResponse> payments
) {
    public PaymentListResponse {
        payments = List.copyOf(payments);
    }

    @Schema(description = "결제 거래 목록 항목")
    public record PaymentItemResponse(
        @Schema(description = "결제 거래의 공개 UUID", format = "uuid", example = "ce03468a-6eb1-4e55-9f23-68a3404f0fca")
        String paymentId,
        @Schema(description = "결제 거래 유형")
        PaymentTransactionType paymentType,
        @Schema(description = "결제 거래 처리 상태")
        PaymentTransactionStatus status,
        @Schema(description = "결제 또는 취소 금액(원)", example = "32700")
        Long amount,
        @Schema(description = "결제 거래 발생 시각", example = "2026-09-10T15:30:00")
        LocalDateTime occurredAt
    ) {
    }
}
