package com.chapchap.subscription.domain.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 첫 구독을 결제하기 전에 화면에 표시할 예상 이용 기간과 금액 구성이다. */
@Schema(description = "첫 구독 예상 이용 기간 및 결제금액. 실제 결제 요청 시 금액은 다시 계산된다.")
public record FirstSubscriptionPreviewResponse(
    @Schema(description = "예상 이용 기간 시작일", example = "2026-09-14")
    LocalDate periodStartDate,
    @Schema(description = "예상 이용 기간 종료일", example = "2026-10-11")
    LocalDate periodEndDate,
    @Schema(description = "식사 금액 합계(원)", example = "228900")
    long totalMealAmount,
    @Schema(description = "배송비 합계(원)", example = "14400")
    long totalDeliveryFee,
    @Schema(description = "첫 구독 할인 금액 합계(원)", example = "20000")
    long totalDiscountAmount,
    @Schema(description = "예상 결제금액(원): 식사 금액 + 배송비 - 할인 금액", example = "223300")
    long paymentAmount
) {
    /** 기간·금액 구성과 예상 결제금액의 합계 관계를 검증한다. */
    public FirstSubscriptionPreviewResponse {
        if (periodStartDate == null || periodEndDate == null || periodEndDate.isBefore(periodStartDate)) {
            throw new IllegalArgumentException("preview period dates must be valid");
        }
        if (totalMealAmount <= 0L || totalDeliveryFee < 0L || totalDiscountAmount < 0L || paymentAmount <= 0L) {
            throw new IllegalArgumentException("preview amounts must be valid");
        }
        long calculatedPaymentAmount = Math.subtractExact(
            Math.addExact(totalMealAmount, totalDeliveryFee), totalDiscountAmount
        );
        if (paymentAmount != calculatedPaymentAmount) {
            throw new IllegalArgumentException("paymentAmount must match the preview amount composition");
        }
    }
}
