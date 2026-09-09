package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationPreparation;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationType;
import com.chapchap.subscription.global.exception.payment.PaymentTransactionProcessingException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionCancellationNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionKafkaDeliveryCompletedException;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 기간 전액 취소의 환불과 원 결제별 취소 거래를 외부 호출 전에 확정한다. */
@Service
public class PeriodRefundPreparationService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPeriodRepository periods;
    private final OrderRepository orders;
    private final PaymentAllocationRepository allocations;
    private final PaymentTransactionRepository payments;
    private final RefundRepository refunds;

    public PeriodRefundPreparationService(SubscriptionRepository subscriptions,
        SubscriptionPeriodRepository periods, OrderRepository orders,
        PaymentAllocationRepository allocations, PaymentTransactionRepository payments,
        RefundRepository refunds) {
        this.subscriptions = subscriptions;
        this.periods = periods;
        this.orders = orders;
        this.allocations = allocations;
        this.payments = payments;
        this.refunds = refunds;
    }

    @Transactional
    public PreparedPeriodRefund prepare(SubscriptionCancellationPreparation prepared) {
        var subscription = subscriptions.findWithLockById(prepared.subscriptionId())
            .orElseThrow(SubscriptionCancellationNotAllowedException::new);
        var period = periods.findWithLockById(prepared.targetPeriodId())
            .orElseThrow(SubscriptionCancellationNotAllowedException::new);
        if (period.getStatus() != SubscriptionPeriodStatus.SCHEDULED
            || !period.getSubscriptionId().equals(subscription.getId())) {
            throw new SubscriptionCancellationNotAllowedException();
        }
        if (prepared.cancellationType() == SubscriptionCancellationType.CANCELLATION_BEFORE_START
            && subscription.getStatus() != SubscriptionStatus.SCHEDULED) {
            throw new SubscriptionCancellationNotAllowedException();
        }
        if (prepared.cancellationType() == SubscriptionCancellationType.NEXT_PERIOD_FULL_CANCELLATION
            && subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            throw new SubscriptionCancellationNotAllowedException();
        }
        if (payments.existsBySubscriptionIdAndStatus(subscription.getId(), PaymentTransactionStatus.PROCESSING)) {
            throw new PaymentTransactionProcessingException();
        }
        if (orders.existsBySubscriptionPeriodIdAndKafkaDeliveryStatus(period.getId(), OrderKafkaDeliveryStatus.COMPLETED)) {
            throw new SubscriptionKafkaDeliveryCompletedException();
        }

        var existing = refunds.findBySubscriptionPeriodId(period.getId());
        if (existing.isPresent()) return continueExisting(prepared, existing.get(), subscription.getUserId());

        Map<Long, Long> amountByOriginal = remainingAmountByOriginal(prepared.targetOrderIds());
        if (amountByOriginal.isEmpty()) throw new SubscriptionCancellationNotAllowedException();
        long total = amountByOriginal.values().stream().reduce(0L, Math::addExact);
        RefundType refundType = prepared.cancellationType() == SubscriptionCancellationType.CANCELLATION_BEFORE_START
            ? RefundType.CANCELLATION_BEFORE_START : RefundType.NEXT_PERIOD_FULL_CANCELLATION;
        Refund refund = refunds.saveAndFlush(Refund.createPeriodCancellation(
            subscription.getId(), period.getId(), refundType, total
        ));
        return createNext(prepared, refund, subscription.getUserId(), amountByOriginal);
    }

    private PreparedPeriodRefund continueExisting(SubscriptionCancellationPreparation prepared,
        Refund refund, Long userId) {
        if (refund.getStatus() == RefundStatus.REVIEW_REQUIRED) {
            return new PreparedPeriodRefund(refund.getId(), refund.getStatus(), List.of());
        }
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return new PreparedPeriodRefund(refund.getId(), refund.getStatus(), List.of());
        }
        if (refund.getStatus() == RefundStatus.FAILED) {
            refund.retry();
            PaymentTransaction failed = payments.findAllByRefundIdOrderByOccurredAtAscIdAsc(refund.getId())
                .stream().filter(value -> value.getStatus() == PaymentTransactionStatus.FAILED)
                .findFirst().orElseThrow(() -> new IllegalStateException("Failed refund has no failed transaction"));
            failed.retryCancellation(newIdempotencyKey());
            return new PreparedPeriodRefund(refund.getId(), refund.getStatus(), List.of(failed.getId()));
        }
        return createNext(prepared, refund, userId, remainingAmountByOriginal(prepared.targetOrderIds()));
    }

    private PreparedPeriodRefund createNext(SubscriptionCancellationPreparation prepared,
        Refund refund, Long userId, Map<Long, Long> amountByOriginal) {
        if (amountByOriginal.isEmpty()) {
            if (refund.getStatus() != RefundStatus.COMPLETED) {
                throw new IllegalStateException("Pending refund has no remaining allocation");
            }
            return new PreparedPeriodRefund(refund.getId(), refund.getStatus(), List.of());
        }
        Map<Long, PaymentTransaction> originals = new HashMap<>();
        payments.findAllById(amountByOriginal.keySet()).forEach(value -> originals.put(value.getId(), value));
        if (originals.size() != amountByOriginal.size()) throw new IllegalStateException("Original payment is missing");
        PaymentTransaction original = originals.values().stream().sorted(Comparator
            .comparingInt(this::priority)
            .thenComparing(PaymentTransaction::getOccurredAt, Comparator.reverseOrder())
            .thenComparing(PaymentTransaction::getId, Comparator.reverseOrder())).findFirst().orElseThrow();
        long amount = amountByOriginal.get(original.getId());
        validateOriginal(original, prepared.subscriptionId(), prepared.targetPeriodId(), amount);
        var period = periods.findById(prepared.targetPeriodId()).orElseThrow();
        PaymentTransactionType type = prepared.cancellationType() == SubscriptionCancellationType.CANCELLATION_BEFORE_START
            ? PaymentTransactionType.CANCELLATION_BEFORE_START
            : PaymentTransactionType.NEXT_PERIOD_FULL_CANCELLATION;
        PaymentTransaction cancellation = payments.saveAndFlush(PaymentTransaction.createCancellation(
            userId, prepared.subscriptionId(), prepared.targetPeriodId(), refund.getId(), original.getId(),
            type, amount, prepared.referenceAt(), period.getPeriodStartDate(), period.getPeriodEndDate(),
            newIdempotencyKey(), prepared.referenceAt()
        ));
        return new PreparedPeriodRefund(refund.getId(), refund.getStatus(), List.of(cancellation.getId()));
    }

    private Map<Long, Long> remainingAmountByOriginal(List<Long> orderIds) {
        Map<Long, Long> amountByOriginal = new HashMap<>();
        for (PaymentAllocation allocation : allocations.findAllByOrderIdInOrderByIdAsc(orderIds)) {
            long cancelable = allocation.currentCancelableAmount();
            if (cancelable > 0) amountByOriginal.merge(
                allocation.getOriginalPaymentTransactionId(), cancelable, Math::addExact
            );
        }
        return amountByOriginal;
    }

    private void validateOriginal(PaymentTransaction original, Long subscriptionId, Long periodId, long amount) {
        if (!original.getSubscriptionId().equals(subscriptionId)
            || !original.getSubscriptionPeriodId().equals(periodId)
            || original.getStatus() != PaymentTransactionStatus.SUCCESS
            || original.getCancelableAmount() == null || original.getCancelableAmount() < amount) {
            throw new IllegalStateException("Original payment is not cancellable");
        }
    }

    private int priority(PaymentTransaction transaction) {
        return transaction.getTransactionType() == PaymentTransactionType.SETTING_CHANGE_PAYMENT ? 0 : 1;
    }

    private String newIdempotencyKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
