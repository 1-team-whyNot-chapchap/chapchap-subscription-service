package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import java.util.List;

public record PreparedPeriodRefund(
    Long refundId,
    RefundStatus status,
    List<Long> cancellationTransactionIds
) {
    public PreparedPeriodRefund {
        cancellationTransactionIds = List.copyOf(cancellationTransactionIds);
    }
}
