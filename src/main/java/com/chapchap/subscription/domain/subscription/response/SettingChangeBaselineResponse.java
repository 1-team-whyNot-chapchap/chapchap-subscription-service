package com.chapchap.subscription.domain.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "변경 적용 예정일에 유효한 확정 설정. 현재 이용 정보나 금액 견적이 아니다.")
public record SettingChangeBaselineResponse(
    @Schema(description = "구독 공개 UUID", format = "uuid") String subscriptionId,
    @Schema(description = "조회 시점의 변경 적용 예정일. 예약 또는 확정일이 아니다.") LocalDate effectiveStartDate,
    @Schema(description = "변경 기준 플랜") CurrentSubscriptionResponse.PlanResponse plan,
    @Schema(description = "변경 기준 배송 조건, 월~토 순서")
    List<CurrentSubscriptionResponse.DeliveryConditionResponse> deliveryConditions
) {
    public SettingChangeBaselineResponse {
        deliveryConditions = List.copyOf(deliveryConditions);
    }
}
