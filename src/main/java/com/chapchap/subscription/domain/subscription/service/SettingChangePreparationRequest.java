package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import java.util.List;

/** 결제·환불 확정 전 설정 변경 가능 여부를 검토하기 위한 내부 입력이다. */
public record SettingChangePreparationRequest(String planId, List<DeliveryCondition> deliveryConditions) {
    public record DeliveryCondition(DeliveryWeekday weekday, Integer mealQuantity, String addressId, DeliveryTimeSlot deliveryTimeSlot) {}
}
