package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;

import java.time.LocalDate;
import java.util.List;

/** 설정 변경으로 생성할 변경 대기 주문의 검증 완료 입력이다. */
public record SettingChangeOrderPreparationCommand(
    Long userId,
    Long subscriptionId,
    Long subscriptionSettingId,
    Long termsAgreementId,
    PlanSnapshot plan,
    List<Delivery> deliveries
) {
    public SettingChangeOrderPreparationCommand {
        if (userId == null || subscriptionId == null || termsAgreementId == null || plan == null || deliveries == null) {
            throw new IllegalArgumentException("설정 변경 주문 생성 입력이 누락되었습니다.");
        }
        deliveries = List.copyOf(deliveries);
    }

    /** 변경 대기 설정이 저장된 뒤 생성된 내부 식별자를 주문 생성 입력에 결합한다. */
    public SettingChangeOrderPreparationCommand withSubscriptionSettingId(Long subscriptionSettingId) {
        return new SettingChangeOrderPreparationCommand(
            userId, subscriptionId, subscriptionSettingId, termsAgreementId, plan, deliveries
        );
    }

    public record PlanSnapshot(Long planId, String planName, Long mealUnitPrice) {
    }

    public record AddressSnapshot(
        Long addressId,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethodCode,
        String otherDeliveryRequest,
        String entrancePassword
    ) {
    }

    public record Delivery(
        Long subscriptionPeriodId,
        LocalDate deliveryDate,
        int revisionSequence,
        Long replacementTargetOrderId,
        Long menuId,
        Long menuPlanId,
        Integer menuSequence,
        String menuName,
        Integer mealQuantity,
        AddressSnapshot address,
        OrderDeliveryTimeSlot deliveryTimeSlot
    ) {
    }
}
