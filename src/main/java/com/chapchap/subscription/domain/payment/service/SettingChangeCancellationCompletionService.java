package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.service.SettingChangeAmountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;

@Service
public class SettingChangeCancellationCompletionService {
    private final PaymentTransactionRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentAllocationRepository allocations;
    private final RefundRepository refunds;
    private final SettingChangeAmountService amounts;

    public SettingChangeCancellationCompletionService(PaymentTransactionRepository payments,
        PaymentAttemptRepository attempts, PaymentAllocationRepository allocations,
        RefundRepository refunds, SettingChangeAmountService amounts) {
        this.payments = payments;
        this.attempts = attempts;
        this.allocations = allocations;
        this.refunds = refunds;
        this.amounts = amounts;
    }

    @Transactional
    public RefundStatus complete(Long settingId, PaymentCancellationExecutionResult result) {
        var cancellation = payments.findById(result.cancellationTransactionId()).orElseThrow();
        if (cancellation.getStatus() != PaymentTransactionStatus.PROCESSING
            || !result.idempotencyKey().equals(cancellation.getExternalRequestIdempotencyKey())
            || !cancellation.getTransactionAmount().equals(result.requestedAmount())) {
            throw new IllegalStateException("Cancellation execution result does not match transaction");
        }
        if (attempts.existsByIdempotencyKey(result.idempotencyKey())) {
            throw new IllegalStateException("Cancellation response is already recorded");
        }
        var refund = refunds.findById(cancellation.getRefundId()).orElseThrow();
        int sequence = attempts.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(cancellation.getId()).size() + 1;
        var provider = result.providerResult();
        if (provider.isSucceeded()) {
            attempts.save(PaymentAttempt.cancellationSuccess(cancellation.getId(), result.providerCode(), sequence,
                result.idempotencyKey(), result.requestedAmount(), result.requestedAt(), result.respondedAt(),
                provider.externalPaymentId(), provider.externalCancellationId(), provider.externalResultCode()));
            var original = payments.findById(cancellation.getOriginalPaymentTransactionId()).orElseThrow();
            var oldIds = new HashSet<>(amounts.analyze(settingId).oldOrders().stream().map(value -> value.getId()).toList());
            long remaining = result.requestedAmount();
            for (PaymentAllocation allocation : allocations.findAllByOriginalPaymentTransactionIdOrderByIdAsc(original.getId())) {
                if (remaining == 0) break;
                if (!oldIds.contains(allocation.getOrderId()) || allocation.currentCancelableAmount() == 0) continue;
                long cancelled = Math.min(remaining, allocation.currentCancelableAmount());
                allocation.cancel(cancelled);
                remaining -= cancelled;
            }
            if (remaining != 0) throw new IllegalStateException("Setting change allocations do not cover cancellation");
            original.applySuccessfulCancellation(result.requestedAmount());
            cancellation.markCancellationSucceeded();
            refund.addSuccessfulAmount(result.requestedAmount(), result.respondedAt());
            return refund.getStatus();
        }
        attempts.save(PaymentAttempt.cancellationFailure(cancellation.getId(), result.providerCode(), sequence,
            result.idempotencyKey(), result.requestedAmount(), result.requestedAt(), result.respondedAt(),
            provider.externalPaymentId(), provider.externalResultCode(), provider.failureReason()));
        cancellation.markCancellationFailed();
        refund.markFailed(provider.failureReason());
        return refund.getStatus();
    }
}
