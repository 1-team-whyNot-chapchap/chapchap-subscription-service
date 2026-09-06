package com.chapchap.subscription.domain.subscription.request;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
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
    @Pattern(regexp = "^PLN-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
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
        @Pattern(regexp = "^ADR-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        String addressId,
        @NotNull DeliveryTimeSlot deliveryTimeSlot
    ) {}
}
