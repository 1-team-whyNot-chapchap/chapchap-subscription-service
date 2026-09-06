package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/** 명시적으로 확정된 외부 취소 응답을 거래·배분·환불에 원자적으로 반영한다. */
@Service
public class PaymentCancellationCompletionService {
    private final PaymentTransactionRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentAllocationRepository allocations;
    private final RefundRepository refunds;
    private final OrderRepository orders;

    public PaymentCancellationCompletionService(PaymentTransactionRepository payments,
        PaymentAttemptRepository attempts, PaymentAllocationRepository allocations,
        RefundRepository refunds, OrderRepository orders) {
        this.payments = payments;
        this.attempts = attempts;
        this.allocations = allocations;
        this.refunds = refunds;
        this.orders = orders;
    }

    @Transactional
    public RefundStatus complete(PaymentCancellationExecutionResult result) {
        var cancellation = payments.findById(result.cancellationTransactionId()).orElseThrow();
        if (cancellation.getStatus() != PaymentTransactionStatus.PROCESSING
            || !result.idempotencyKey().equals(cancellation.getExternalRequestIdempotencyKey())
            || cancellation.getTransactionAmount() != result.requestedAmount()) {
            throw new IllegalStateException("Cancellation execution result does not match transaction");
        }
        if (attempts.existsByIdempotencyKey(result.idempotencyKey())) {
            throw new IllegalStateException("Cancellation response is already recorded");
        }
        var refund = refunds.findById(cancellation.getRefundId()).orElseThrow();
        int attemptSequence = attempts
            .findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(cancellation.getId()).size() + 1;
        var provider = result.providerResult();
        if (provider.isSucceeded()) {
            attempts.save(PaymentAttempt.cancellationSuccess(
                cancellation.getId(), result.providerCode(), attemptSequence, result.idempotencyKey(),
                result.requestedAmount(), result.requestedAt(), result.respondedAt(),
                provider.externalPaymentId(), provider.externalCancellationId(), provider.externalResultCode()
            ));
            var original = payments.findById(cancellation.getOriginalPaymentTransactionId()).orElseThrow();
            var targetOrderIds = new HashSet<>(orders.findAllBySubscriptionPeriodId(
                cancellation.getSubscriptionPeriodId()).stream().map(value -> value.getId()).toList());
            var targetAllocations = allocations
                .findAllByOriginalPaymentTransactionIdOrderByIdAsc(original.getId()).stream()
                .filter(value -> targetOrderIds.contains(value.getOrderId()))
                .filter(value -> value.currentCancelableAmount() > 0)
                .toList();
            long allocatedAmount = targetAllocations.stream()
                .mapToLong(PaymentAllocation::currentCancelableAmount).sum();
            if (allocatedAmount != result.requestedAmount()) {
                throw new IllegalStateException("Cancellation allocations do not match requested amount");
            }
            targetAllocations.forEach(value -> value.cancel(value.currentCancelableAmount()));
            original.applySuccessfulCancellation(result.requestedAmount());
            cancellation.markCancellationSucceeded();
            refund.addSuccessfulAmount(result.requestedAmount(), result.respondedAt());
            return refund.getStatus();
        }

        attempts.save(PaymentAttempt.cancellationFailure(
            cancellation.getId(), result.providerCode(), attemptSequence, result.idempotencyKey(),
            result.requestedAmount(), result.requestedAt(), result.respondedAt(),
            provider.externalPaymentId(), provider.externalResultCode(), provider.failureReason()
        ));
        cancellation.markCancellationFailed();
        refund.markFailed(provider.failureReason());
        return refund.getStatus();
    }
}
