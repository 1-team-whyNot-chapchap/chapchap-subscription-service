package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객의 현재 구독 상태와 현재 적용 설정을 제공한다. */
public record CurrentSubscriptionResponse(
        String subscriptionId,
        SubscriptionStatus subscriptionStatus,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime cancellationRequestedAt,
        PlanResponse plan,
        List<DeliveryConditionResponse> deliveryConditions
) {
    public CurrentSubscriptionResponse {
        deliveryConditions = List.copyOf(deliveryConditions);
    }

    public record PlanResponse(
            String planId,
            String name,
            String description,
            Long unitPrice
    ) {
    }

    public record DeliveryConditionResponse(
            DeliveryWeekday weekday,
            Integer mealQuantity,
            DeliveryTimeSlot deliveryTimeSlot,
            AddressResponse address
    ) {
    }

    public record AddressResponse(
            String addressId,
            String name,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2
    ) {
    }
}
