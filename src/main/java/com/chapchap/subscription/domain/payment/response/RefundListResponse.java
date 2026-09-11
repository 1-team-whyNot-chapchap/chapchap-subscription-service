package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객 구독의 환불 업무 목록 응답이다. */
@Schema(description = "인증 고객 구독의 환불 업무 목록")
public record RefundListResponse(
    @Schema(description = "환불 업무 목록")
    List<RefundItemResponse> refunds
) {
    public RefundListResponse {
        refunds = List.copyOf(refunds);
    }

    @Schema(description = "환불 업무 목록 항목")
    public record RefundItemResponse(
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
        LocalDateTime completedAt
    ) {
    }
}
