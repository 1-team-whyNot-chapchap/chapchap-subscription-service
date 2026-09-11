package com.chapchap.subscription.domain.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** DB 상태를 만들지 않고 계산한 구독 설정 변경 예상 결과다. */
@Schema(description = "구독 설정 변경 미리보기 결과")
public record SettingChangePreviewResponse(
    @Schema(description = "기존 변경 대상 주문의 취소 가능 금액(원)", example = "89400")
    long currentAmount,
    @Schema(description = "변경 후 주문 예상 금액(원)", example = "107200")
    long changedAmount,
    @Schema(description = "금액 차이 유형")
    DifferenceType differenceType,
    @Schema(description = "금액 차이 절댓값(원)", example = "17800")
    long differenceAmount,
    @Schema(description = "변경 설정 적용 시작일", example = "2026-09-14")
    LocalDate effectiveStartDate,
    @Schema(description = "최종 확인 뒤 수행할 처리")
    RequiredAction requiredAction
) {
    public enum DifferenceType { INCREASE, DECREASE, NO_PRICE_CHANGE }
    public enum RequiredAction { ADDITIONAL_PAYMENT, REFUND, NONE }
}
