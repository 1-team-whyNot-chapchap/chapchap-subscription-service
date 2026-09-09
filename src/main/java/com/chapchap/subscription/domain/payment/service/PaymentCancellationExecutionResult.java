package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import java.time.LocalDateTime;

public record PaymentCancellationExecutionResult(
    Long cancellationTransactionId,
    PaymentProviderCode providerCode,
    String idempotencyKey,
    long requestedAmount,
    LocalDateTime requestedAt,
    LocalDateTime respondedAt,
    PaymentCancellationResult providerResult
) {
}
