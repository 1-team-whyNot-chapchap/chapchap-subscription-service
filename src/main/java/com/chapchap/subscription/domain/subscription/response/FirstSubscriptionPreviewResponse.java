package com.chapchap.subscription.domain.subscription.response;

import java.time.LocalDate;

/** 첫 구독을 결제하기 전에 화면에 표시할 예상 이용 기간과 금액 구성이다. */
public record FirstSubscriptionPreviewResponse(
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    long totalMealAmount,
    long totalDeliveryFee,
    long totalDiscountAmount,
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
