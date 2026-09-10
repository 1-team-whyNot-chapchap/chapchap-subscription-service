package com.chapchap.subscription.domain.subscription.request;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "구독 설정 변경 조건")
public record SettingChangeRequest(
    @Schema(description = "변경할 플랜의 공개 UUID", format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank
    @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
    String planId,
    @Schema(description = "변경할 1~6개 요일별 배송 조건")
    @NotEmpty @Size(max = 6) List<@Valid DeliveryCondition> deliveryConditions
) {
    public SettingChangeRequest {
        if (deliveryConditions != null) deliveryConditions = List.copyOf(deliveryConditions);
    }

    @Schema(description = "변경할 요일별 배송 조건")
    public record DeliveryCondition(
        @Schema(description = "배송 요일", example = "MONDAY")
        @NotNull DeliveryWeekday weekday,
        @Schema(description = "해당 요일 도시락 수량", minimum = "1", maximum = "6", example = "3")
        @NotNull @Min(1) @Max(6) Integer mealQuantity,
        @Schema(description = "배송지의 공개 UUID", format = "uuid", example = "550e8400-e29b-41d4-a716-446655440001")
        @NotBlank
        @Pattern(regexp = PublicIdFormat.UUID_V4_REGEX)
        String addressId,
        @Schema(description = "배송 시간대", example = "TIME_1100_1300")
        @NotNull DeliveryTimeSlot deliveryTimeSlot
    ) {}
}
