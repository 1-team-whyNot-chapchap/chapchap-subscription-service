package com.chapchap.subscription.domain.subscription.request;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 첫 구독 신청 시 고객이 선택한 플랜과 요일별 배송 조건을 전달한다. */
public record FirstSubscriptionRequest(
    @NotBlank
    @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
    String planId,
    @NotEmpty @Size(max = 6) List<@Valid DeliveryCondition> deliveryConditions
) {
    /** 요청 이후 외부에서 배송 조건 목록을 변경할 수 없도록 불변 복사한다. */
    public FirstSubscriptionRequest {
        if (deliveryConditions != null) {
            deliveryConditions = List.copyOf(deliveryConditions);
        }
    }

    /** 한 배송 요일에 적용할 수량·배송지·배송 시간대를 담는다. */
    public record DeliveryCondition(
        @NotNull DeliveryWeekday weekday,
        @NotNull @Min(1) @Max(6) Integer mealQuantity,
        @NotBlank
        @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
        String addressId,
        @NotNull DeliveryTimeSlot deliveryTimeSlot
    ) {
    }
}
