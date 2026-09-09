package com.chapchap.subscription.domain.payment.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 첫 결제 거래를 외부 요청 전에 준비하는 데 필요한 확정 값이다.
 *
 * @param userId 결제 대상 고객의 내부 식별자
 * @param subscriptionId 결제 대상 구독의 내부 식별자
 * @param subscriptionPeriodId 첫 결제 대상 이용 기간의 내부 식별자
 * @param transactionAmount 첫 이용 기간의 전체 결제금액
 * @param processingReferenceAt 기간·주문·금액 계산에 사용한 고정 기준 시각
 * @param periodStartDate 결제 대상 이용 기간 시작일
 * @param periodEndDate 결제 대상 이용 기간 종료일
 */
public record FirstPaymentPrepareCommand(
    Long userId,
    Long subscriptionId,
    Long subscriptionPeriodId,
    Long transactionAmount,
    LocalDateTime processingReferenceAt,
    LocalDate periodStartDate,
    LocalDate periodEndDate
) {
    /** 식별자·금액·기간 값이 첫 결제 거래를 생성할 수 있는지 확인한다. */
    public FirstPaymentPrepareCommand {
        requirePositive(userId, "userId");
        requirePositive(subscriptionId, "subscriptionId");
        requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        requirePositive(transactionAmount, "transactionAmount");
        requireNonNull(processingReferenceAt, "processingReferenceAt");
        requireNonNull(periodStartDate, "periodStartDate");
        requireNonNull(periodEndDate, "periodEndDate");
        if (periodEndDate.isBefore(periodStartDate)) {
            throw new IllegalArgumentException("periodEndDate must not be before periodStartDate");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
