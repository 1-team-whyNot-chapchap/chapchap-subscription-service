package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 환불 집계와 연결된 원 결제 취소 거래를 제공하는 환불 상세 응답이다. */
@Schema(description = "환불 상세와 연결된 원 결제 취소 거래")
public record RefundDetailResponse(
    @Schema(description = "환불의 공개 UUID", format = "uuid", example = "119ec1f0-1aa5-4a6e-bca1-865d83a23f15")
    String refundId,
    @Schema(description = "환불 발생 유형")
    RefundType refundType,
    @Schema(description = "환불 처리 상태")
    RefundStatus status,
    @Schema(description = "환불 요청 금액(원)", example = "32700")
    Long requestedAmount,
    @Schema(description = "환불 완료 금액(원)", example = "32700")
    Long refundedAmount,
    @Schema(description = "미처리 환불 금액(원)", example = "0")
    Long unprocessedAmount,
    @Schema(description = "환불 요청 시각", example = "2026-09-10T15:30:00")
    LocalDateTime requestedAt,
    @Schema(description = "환불 완료 시각", nullable = true, example = "2026-09-10T15:30:01")
    LocalDateTime completedAt,
    @Schema(description = "연결된 원 결제 취소 거래 목록")
    List<CancellationResponse> cancellations
) {
    public RefundDetailResponse {
        cancellations = List.copyOf(cancellations);
    }

    @Schema(description = "원 결제 취소 거래 항목")
    public record CancellationResponse(
        @Schema(description = "취소 결제 거래의 공개 UUID", format = "uuid", example = "ce03468a-6eb1-4e55-9f23-68a3404f0fca")
        String paymentId,
        @Schema(description = "취소 대상 원 결제 거래의 공개 UUID", format = "uuid", example = "77874e8d-7aea-47e4-a2e2-1e679cf3b3b4")
        String originalPaymentId,
        @Schema(description = "취소 결제 거래 처리 상태")
        PaymentTransactionStatus status,
        @Schema(description = "취소 금액(원)", example = "32700")
        Long amount,
        @Schema(description = "취소 거래 발생 시각", example = "2026-09-10T15:30:01")
        LocalDateTime occurredAt
    ) {
    }
}
