package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 배송 건의 원 결제 취소 응답과 주문 배분 집계를 한 트랜잭션으로 확정한다. */
@Service
public class DeliveryRefundCancellationCompletionService {
    private final PaymentTransactionRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentAllocationRepository allocations;
    private final RefundRepository refunds;

    public DeliveryRefundCancellationCompletionService(
        PaymentTransactionRepository payments,
        PaymentAttemptRepository attempts,
        PaymentAllocationRepository allocations,
        RefundRepository refunds
    ) {
        this.payments = payments;
        this.attempts = attempts;
        this.allocations = allocations;
        this.refunds = refunds;
    }

    @Transactional
    public RefundStatus complete(PaymentCancellationExecutionResult result) {
        var cancellation = payments.findWithLockById(result.cancellationTransactionId()).orElseThrow();
        if (cancellation.getTransactionType() != PaymentTransactionType.DELIVERY_PARTIAL_CANCELLATION
            || cancellation.getStatus() != PaymentTransactionStatus.PROCESSING
            || !result.idempotencyKey().equals(cancellation.getExternalRequestIdempotencyKey())
            || !cancellation.getTransactionAmount().equals(result.requestedAmount())) {
            throw new IllegalStateException("Delivery cancellation result does not match transaction");
        }
        if (attempts.existsByIdempotencyKey(result.idempotencyKey())) {
            throw new IllegalStateException("Cancellation response is already recorded");
        }

        var refund = refunds.findWithLockById(cancellation.getRefundId()).orElseThrow();
        int sequence = attempts.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(cancellation.getId()).size() + 1;
        var provider = result.providerResult();
        if (!provider.isSucceeded()) {
            attempts.save(PaymentAttempt.cancellationFailure(
                cancellation.getId(), result.providerCode(), sequence, result.idempotencyKey(),
                result.requestedAmount(), result.requestedAt(), result.respondedAt(),
                provider.externalPaymentId(), provider.externalResultCode(), provider.failureReason()));
            cancellation.markCancellationFailed();
            refund.markFailed(provider.failureReason());
            return refund.getStatus();
        }

        var original = payments.findWithLockById(cancellation.getOriginalPaymentTransactionId()).orElseThrow();
        List<PaymentAllocation> orderAllocations = allocations
            .findWithLockAllByOrderIdAndOriginalPaymentTransactionId(
                refund.getOrderId(), original.getId());
        if (orderAllocations.size() != 1) {
            throw new IllegalStateException("Delivery refund must have exactly one allocation per original payment");
        }
        PaymentAllocation allocation = orderAllocations.getFirst();
        if (allocation.currentCancelableAmount() < result.requestedAmount()) {
            throw new IllegalStateException("Delivery allocation cancellation balance is insufficient");
        }

        attempts.save(PaymentAttempt.cancellationSuccess(
            cancellation.getId(), result.providerCode(), sequence, result.idempotencyKey(),
            result.requestedAmount(), result.requestedAt(), result.respondedAt(),
            provider.externalPaymentId(), provider.externalCancellationId(), provider.externalResultCode()));
        allocation.cancel(result.requestedAmount());
        original.applySuccessfulCancellation(result.requestedAmount());
        cancellation.markCancellationSucceeded();
        refund.addSuccessfulAmount(result.requestedAmount(), result.respondedAt());
        return refund.getStatus();
    }
}
