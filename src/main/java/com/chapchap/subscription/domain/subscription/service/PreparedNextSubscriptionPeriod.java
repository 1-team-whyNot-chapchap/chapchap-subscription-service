package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 정기결제 준비 단계에서 고정한 다음 이용 기간과 주문 금액 스냅샷이다. */
public record PreparedNextSubscriptionPeriod(
    Long userId,
    Long subscriptionId,
    Long subscriptionPeriodId,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    LocalDateTime processingReferenceAt,
    Long totalAmount,
    List<PaymentAllocationCommand> allocations
) {
    public PreparedNextSubscriptionPeriod {
        allocations = List.copyOf(allocations);
    }
}
