package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;

import java.time.LocalDate;
import java.util.List;

/**
 * 결제·환불 확정 전 계산된 다음 구독 설정 버전의 저장 후보이다.
 * 이 객체를 만드는 과정은 어떤 엔터티도 저장하거나 상태를 변경하지 않는다.
 */
public record SettingChangeDraft(
    int settingSequence,
    Long planId,
    LocalDate effectiveStartDate,
    List<DeliveryCondition> deliveryConditions
) {
    public SettingChangeDraft {
        deliveryConditions = List.copyOf(deliveryConditions);
    }

    public record DeliveryCondition(
        DeliveryWeekday weekday,
        int mealQuantity,
        Long addressId,
        DeliveryTimeSlot deliveryTimeSlot
    ) {
    }
}
