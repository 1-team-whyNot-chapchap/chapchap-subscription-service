package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 현재 결제금액의 사용처를 새 주문으로 옮기고 설정 변경을 같은 트랜잭션에서 확정한다. */
@Service
public class SettingChangeFinalizationService {
    private final SettingChangeAmountService amounts;
    private final PaymentAllocationRepository allocations;
    private final PaymentTransactionRepository payments;
    private final SettingChangeCompletionService completion;

    public SettingChangeFinalizationService(SettingChangeAmountService amounts,
        PaymentAllocationRepository allocations, PaymentTransactionRepository payments,
        SettingChangeCompletionService completion) {
        this.amounts = amounts;
        this.allocations = allocations;
        this.payments = payments;
        this.completion = completion;
    }

    @Transactional
    public void approve(Long settingId, LocalDateTime completedAt) {
        SettingChangeAmountSnapshot snapshot = amounts.analyze(settingId);
        List<Long> oldOrderIds = snapshot.oldOrders().stream().map(Order::getId).toList();
        List<Long> newOrderIds = snapshot.newOrders().stream().map(Order::getId).toList();
        List<PaymentAllocation> newExisting = newOrderIds.isEmpty()
            ? List.of() : allocations.findAllByOrderIdInOrderByIdAsc(newOrderIds);
        long available = snapshot.oldAllocations().stream().mapToLong(PaymentAllocation::currentCancelableAmount).sum()
            + newExisting.stream().mapToLong(PaymentAllocation::currentCancelableAmount).sum();
        if (available != snapshot.newAmount()) {
            throw new IllegalStateException("Available payment balance does not equal new order amount");
        }

        Map<Long, Long> remainingByOrder = new LinkedHashMap<>();
        snapshot.newOrders().stream().sorted(Comparator.comparing(Order::getDeliveryDate).thenComparing(Order::getId))
            .forEach(order -> remainingByOrder.put(order.getId(), order.getActualAllocatedAmount()));
        newExisting.forEach(allocation -> remainingByOrder.compute(allocation.getOrderId(),
            (id, remaining) -> Math.subtractExact(remaining, allocation.currentCancelableAmount())));

        Map<Long, Long> sourceByOriginal = new HashMap<>();
        snapshot.oldAllocations().forEach(source -> {
            if (source.currentCancelableAmount() > 0) sourceByOriginal.merge(
                source.getOriginalPaymentTransactionId(), source.currentCancelableAmount(), Math::addExact);
        });
        Map<Long, PaymentTransaction> originals = new HashMap<>();
        payments.findAllById(new ArrayList<>(sourceByOriginal.keySet()))
            .forEach(payment -> originals.put(payment.getId(), payment));
        List<PaymentAllocation> replacements = new ArrayList<>();
        List<PaymentTransaction> orderedOriginals = originals.values().stream().sorted(Comparator
            .comparingInt((PaymentTransaction payment) -> payment.getTransactionType() == PaymentTransactionType.SETTING_CHANGE_PAYMENT ? 0 : 1)
            .thenComparing(PaymentTransaction::getOccurredAt, Comparator.reverseOrder())
            .thenComparing(PaymentTransaction::getId, Comparator.reverseOrder())).toList();
        for (PaymentTransaction original : orderedOriginals) {
            long sourceRemaining = sourceByOriginal.get(original.getId());
            if (original.getStatus() != PaymentTransactionStatus.SUCCESS
                || !original.getSubscriptionId().equals(snapshot.setting().getSubscriptionId())
                || original.getCancelableAmount() == null || original.getCancelableAmount() < sourceRemaining) {
                throw new IllegalStateException("Original payment cannot fund the new orders");
            }
            for (Map.Entry<Long, Long> target : remainingByOrder.entrySet()) {
                if (sourceRemaining == 0) break;
                if (target.getValue() == 0) continue;
                long assigned = Math.min(sourceRemaining, target.getValue());
                replacements.add(PaymentAllocation.create(target.getKey(), original.getId(),
                    allocationType(original.getTransactionType()), assigned));
                target.setValue(target.getValue() - assigned);
                sourceRemaining -= assigned;
            }
            if (sourceRemaining != 0) throw new IllegalStateException("Old payment balance could not be reallocated");
        }
        if (remainingByOrder.values().stream().anyMatch(value -> value != 0)) {
            throw new IllegalStateException("New orders are not fully allocated");
        }
        if (!oldOrderIds.isEmpty()) allocations.deleteAllByOrderIdIn(oldOrderIds);
        allocations.saveAll(replacements);
        completion.complete(settingId, SettingChangeCompletionStatus.APPROVED, completedAt);
    }

    private PaymentAllocationType allocationType(PaymentTransactionType type) {
        return switch (type) {
            case FIRST_SUBSCRIPTION_PAYMENT -> PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT;
            case REGULAR_PAYMENT -> PaymentAllocationType.REGULAR_PAYMENT;
            case SETTING_CHANGE_PAYMENT -> PaymentAllocationType.SETTING_CHANGE_PAYMENT;
            default -> throw new IllegalStateException("Cancellation transaction cannot fund an order");
        };
    }
}
