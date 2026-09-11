package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 첫 결제 성공 또는 기존 처리 중 재요청에 반환하는 구독·이용 기간 정보다. */
@Schema(description = "첫 구독 결제 요청 결과")
public record FirstSubscriptionResponse(
    @Schema(description = "구독의 공개 UUID", format = "uuid", example = "4bb90ac5-9e29-446e-aee6-26b7c8780ca2")
    String subscriptionId,
    @Schema(description = "구독 처리 상태")
    SubscriptionStatus subscriptionStatus,
    @Schema(description = "생성된 이용 기간 시작일", example = "2026-09-14")
    LocalDate periodStartDate,
    @Schema(description = "생성된 이용 기간 종료일", example = "2026-10-11")
    LocalDate periodEndDate
) {
}
