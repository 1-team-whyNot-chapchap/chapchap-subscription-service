package com.chapchap.subscription.domain.payment.service.result;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;

import java.util.List;

/** 정기결제 외부 호출에 필요한 거래와 주문별 금액 스냅샷이다. */
public record PreparedRegularPayment(
    Long paymentTransactionId,
    Long subscriptionPeriodId,
    PaymentTransactionStatus status,
    List<PaymentAllocationCommand> allocations,
    boolean paymentRequired
) {
    public PreparedRegularPayment {
        allocations = List.copyOf(allocations);
    }
}
