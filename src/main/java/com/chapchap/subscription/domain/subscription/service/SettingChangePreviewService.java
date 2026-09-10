package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderService;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import com.chapchap.subscription.domain.subscription.response.SettingChangePreviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 설정 변경 조건을 저장·결제·환불 없이 검증하고 예상 결과를 계산한다. */
@Service
public class SettingChangePreviewService {
    private final SettingChangePreparationService preparation;
    private final SettingChangeOrderPlanService orderPlans;
    private final SettingChangeOrderService orders;
    private final PaymentAllocationRepository allocations;

    public SettingChangePreviewService(
        SettingChangePreparationService preparation,
        SettingChangeOrderPlanService orderPlans,
        SettingChangeOrderService orders,
        PaymentAllocationRepository allocations
    ) {
        this.preparation = preparation;
        this.orderPlans = orderPlans;
        this.orders = orders;
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public SettingChangePreviewResponse preview(Long userId, SettingChangeRequest request) {
        SettingChangePreparationResult prepared = preparation.prepare(userId, toPreparationRequest(request));
        SettingChangeOrderPreparationCommand orderPlan = orderPlans.plan(prepared);
        long currentAmount = prepared.replaceableOrderIds().isEmpty()
            ? 0L
            : allocations.findAllByOrderIdInOrderByIdAsc(prepared.replaceableOrderIds()).stream()
                .mapToLong(PaymentAllocation::currentCancelableAmount)
                .sum();
        long changedAmount = orders.calculatePlannedAmount(orderPlan);
        return response(currentAmount, changedAmount, prepared.effectiveStartDate());
    }

    private SettingChangePreparationRequest toPreparationRequest(SettingChangeRequest request) {
        return new SettingChangePreparationRequest(request.planId(), request.deliveryConditions().stream()
            .map(value -> new SettingChangePreparationRequest.DeliveryCondition(value.weekday(), value.mealQuantity(),
                value.addressId(), value.deliveryTimeSlot()))
            .toList());
    }

    private SettingChangePreviewResponse response(long currentAmount, long changedAmount, java.time.LocalDate effectiveStartDate) {
        if (changedAmount > currentAmount) {
            return new SettingChangePreviewResponse(currentAmount, changedAmount,
                SettingChangePreviewResponse.DifferenceType.INCREASE, changedAmount - currentAmount,
                effectiveStartDate, SettingChangePreviewResponse.RequiredAction.ADDITIONAL_PAYMENT);
        }
        if (changedAmount < currentAmount) {
            return new SettingChangePreviewResponse(currentAmount, changedAmount,
                SettingChangePreviewResponse.DifferenceType.DECREASE, currentAmount - changedAmount,
                effectiveStartDate, SettingChangePreviewResponse.RequiredAction.REFUND);
        }
        return new SettingChangePreviewResponse(currentAmount, changedAmount,
            SettingChangePreviewResponse.DifferenceType.NO_PRICE_CHANGE, 0L,
            effectiveStartDate, SettingChangePreviewResponse.RequiredAction.NONE);
    }
}
