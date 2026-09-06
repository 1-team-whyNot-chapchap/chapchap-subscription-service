package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 배송 건 환불을 중복 없이 만들고 원 결제별 다음 취소 거래를 준비한다. */
@Service
public class DeliveryRefundPreparationService {
    private final OrderRepository orders;
    private final RefundRepository refunds;
    private final PaymentAllocationRepository allocations;
    private final PaymentTransactionRepository payments;
    private final KstReferenceTimeProvider time;

    public DeliveryRefundPreparationService(
        OrderRepository orders,
        RefundRepository refunds,
        PaymentAllocationRepository allocations,
        PaymentTransactionRepository payments,
        KstReferenceTimeProvider time
    ) {
        this.orders = orders;
        this.refunds = refunds;
        this.allocations = allocations;
        this.payments = payments;
        this.time = time;
    }

    @Transactional
    public PreparedDeliveryRefund start(DeliveryRefundCommand command) {
        Order order = orders.findWithLockByPublicId(command.orderId())
            .orElseThrow(() -> new DeliveryRefundContractException("Delivery refund order was not found"));
        if (!order.getUserId().equals(command.userId())) {
            throw new DeliveryRefundContractException("Delivery refund user and order owner do not match");
        }

        var existingDelivery = refunds.findByExternalDeliveryId(command.deliveryId());
        if (existingDelivery.isPresent()) {
            Refund refund = existingDelivery.get();
            if (!order.getId().equals(refund.getOrderId())) {
                throw new DeliveryRefundContractException("Delivery id is linked to another order");
            }
            return new PreparedDeliveryRefund(refund.getId(), refund.getStatus(), null, true);
        }
        if (refunds.findByOrderId(order.getId()).isPresent()) {
            throw new DeliveryRefundContractException("Order is already linked to another refund");
        }

        Refund refund = refunds.saveAndFlush(Refund.createDeliveryPartialCancellation(
            order.getSubscriptionId(), order.getId(), command.deliveryId(), order.getActualAllocatedAmount()));
        if (!hasSufficientAllocation(order)) {
            refund.markFailed("Stored payment allocation is insufficient for the delivery refund");
            return new PreparedDeliveryRefund(refund.getId(), refund.getStatus(), null, false);
        }
        return prepareNext(refund, order, false);
    }

    @Transactional
    public PreparedDeliveryRefund prepareNext(Long refundId) {
        Refund refund = refunds.findWithLockById(refundId).orElseThrow();
        if (refund.getStatus() != RefundStatus.PENDING) {
            return new PreparedDeliveryRefund(refund.getId(), refund.getStatus(), null, false);
        }
        Order order = orders.findById(refund.getOrderId()).orElseThrow();
        return prepareNext(refund, order, false);
    }

    private PreparedDeliveryRefund prepareNext(Refund refund, Order order, boolean duplicate) {
        long remaining = refund.getRefundAmount() - refund.getSuccessfulRefundAmount();
        if (remaining <= 0) {
            throw new IllegalStateException("Pending delivery refund has no remaining amount");
        }

        List<PaymentAllocation> orderAllocations = allocations.findAllByOrderIdOrderByIdAsc(order.getId());
        Map<Long, PaymentTransaction> originals = originals(orderAllocations);
        Set<Long> alreadyPrepared = new HashSet<>(payments.findAllByRefundIdOrderByOccurredAtAscIdAsc(refund.getId())
            .stream().map(PaymentTransaction::getOriginalPaymentTransactionId).toList());

        PaymentAllocation selected = orderAllocations.stream()
            .filter(allocation -> allocation.currentCancelableAmount() > 0)
            .filter(allocation -> !alreadyPrepared.contains(allocation.getOriginalPaymentTransactionId()))
            .filter(allocation -> originals.containsKey(allocation.getOriginalPaymentTransactionId()))
            .sorted(Comparator.comparing(
                (PaymentAllocation allocation) -> originals.get(allocation.getOriginalPaymentTransactionId()),
                originalPriority()
            ))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Delivery refund allocation does not cover remaining amount"));

        PaymentTransaction original = payments.findWithLockById(selected.getOriginalPaymentTransactionId())
            .orElseThrow();
        long cancelAmount = Math.min(remaining, selected.currentCancelableAmount());
        long reservedAmount = payments.findAllByOriginalPaymentTransactionIdAndStatus(
            original.getId(), PaymentTransactionStatus.PROCESSING).stream()
            .filter(transaction -> isCancellation(transaction.getTransactionType()))
            .mapToLong(PaymentTransaction::getTransactionAmount)
            .sum();
        if (original.getStatus() != PaymentTransactionStatus.SUCCESS
            || !original.getSubscriptionId().equals(order.getSubscriptionId())
            || original.getCancelableAmount() == null
            || original.getCancelableAmount() - reservedAmount < cancelAmount) {
            refund.markFailed("Original payment balance is insufficient for the delivery refund");
            return new PreparedDeliveryRefund(refund.getId(), refund.getStatus(), null, duplicate);
        }

        var now = time.now();
        PaymentTransaction cancellation = payments.saveAndFlush(PaymentTransaction.createDeliveryCancellation(
            original.getUserId(), original.getSubscriptionId(), original.getSubscriptionPeriodId(),
            refund.getId(), original.getId(), cancelAmount, now,
            original.getPeriodStartDate(), original.getPeriodEndDate(),
            UUID.randomUUID().toString().replace("-", ""), now));
        return new PreparedDeliveryRefund(refund.getId(), refund.getStatus(), cancellation.getId(), duplicate);
    }

    private boolean hasSufficientAllocation(Order order) {
        List<PaymentAllocation> values = allocations.findAllByOrderIdOrderByIdAsc(order.getId());
        if (values.isEmpty()) return false;
        Map<Long, PaymentTransaction> originals = originals(values);
        long available = values.stream()
            .filter(value -> {
                PaymentTransaction original = originals.get(value.getOriginalPaymentTransactionId());
                return original != null
                    && original.getStatus() == PaymentTransactionStatus.SUCCESS
                    && original.getSubscriptionId().equals(order.getSubscriptionId());
            })
            .mapToLong(PaymentAllocation::currentCancelableAmount)
            .sum();
        return available >= order.getActualAllocatedAmount();
    }

    private Map<Long, PaymentTransaction> originals(List<PaymentAllocation> values) {
        Map<Long, PaymentTransaction> result = new HashMap<>();
        payments.findAllById(values.stream().map(PaymentAllocation::getOriginalPaymentTransactionId).toList())
            .forEach(value -> result.put(value.getId(), value));
        return result;
    }

    private Comparator<PaymentTransaction> originalPriority() {
        return Comparator
            .comparingInt((PaymentTransaction value) ->
                value.getTransactionType() == PaymentTransactionType.SETTING_CHANGE_PAYMENT ? 0 : 1)
            .thenComparing(PaymentTransaction::getOccurredAt, Comparator.reverseOrder())
            .thenComparing(PaymentTransaction::getId, Comparator.reverseOrder());
    }

    private boolean isCancellation(PaymentTransactionType type) {
        return type == PaymentTransactionType.CANCELLATION_BEFORE_START
            || type == PaymentTransactionType.NEXT_PERIOD_FULL_CANCELLATION
            || type == PaymentTransactionType.SETTING_CHANGE_PARTIAL_CANCELLATION
            || type == PaymentTransactionType.DELIVERY_PARTIAL_CANCELLATION;
    }
}
