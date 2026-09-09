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

public record SettingChangeRequest(
    @NotBlank
    @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
    String planId,
    @NotEmpty @Size(max = 6) List<@Valid DeliveryCondition> deliveryConditions
) {
    public SettingChangeRequest {
        if (deliveryConditions != null) deliveryConditions = List.copyOf(deliveryConditions);
    }

    public record DeliveryCondition(
        @NotNull DeliveryWeekday weekday,
        @NotNull @Min(1) @Max(6) Integer mealQuantity,
        @NotBlank
        @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
        String addressId,
        @NotNull DeliveryTimeSlot deliveryTimeSlot
    ) {}
}
