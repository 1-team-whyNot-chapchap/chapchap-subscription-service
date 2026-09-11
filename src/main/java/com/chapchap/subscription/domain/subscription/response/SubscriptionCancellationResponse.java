package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "구독 해지 처리 결과")
public record SubscriptionCancellationResponse(
    @Schema(description = "해지 처리 유형")
    SubscriptionCancellationType cancellationType,
    @Schema(description = "해지 처리 후 구독 상태")
    SubscriptionStatus subscriptionStatus,
    @Schema(description = "해지 처리 후 이용 기간 상태")
    SubscriptionPeriodStatus periodStatus,
    @Schema(description = "구독 해지 요청 시각", example = "2026-09-10T15:30:00")
    LocalDateTime cancellationRequestedAt,
    @Schema(description = "해지로 발생한 환불 요약. 환불이 없으면 null", nullable = true)
    RefundSummary refund
) {
    @Schema(description = "구독 해지 환불 요약")
    public record RefundSummary(
        @Schema(description = "환불의 공개 UUID", format = "uuid", example = "119ec1f0-1aa5-4a6e-bca1-865d83a23f15")
        String refundId,
        @Schema(description = "환불 처리 상태")
        RefundStatus status,
        @Schema(description = "환불 요청 금액(원)", example = "32700")
        Long requestedAmount,
        @Schema(description = "환불 완료 금액(원)", example = "32700")
        Long refundedAmount,
        @Schema(description = "미처리 환불 금액(원)", example = "0")
        Long unprocessedAmount
    ) {
    }
}
