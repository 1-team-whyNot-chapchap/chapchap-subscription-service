package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "구독 설정 변경 처리 결과")
public record SettingChangeResponse(
    @Schema(description = "구독 설정 변경 처리 상태")
    SubscriptionSettingStatus settingStatus,
    @Schema(description = "기존 설정 대비 금액 차이 유형")
    DifferenceType differenceType,
    @Schema(description = "기존 설정 대비 결제 금액 차이(원)", example = "32700")
    long differenceAmount,
    @Schema(description = "변경 설정 적용 시작일", example = "2026-09-14")
    LocalDate effectiveStartDate,
    @Schema(description = "차액 추가 결제의 고객 확인 필요 여부", example = "true")
    boolean paymentConfirmationRequired,
    @Schema(description = "현재 선택된 자동결제수단. 없으면 null", nullable = true)
    CurrentPaymentMethod currentPaymentMethod,
    @Schema(description = "환불 처리 결과. 환불이 없으면 null", nullable = true)
    RefundResult refund
) {
    @Schema(description = "금액 차이 유형")
    public enum DifferenceType { INCREASE, DECREASE, NO_PRICE_CHANGE }
    @Schema(description = "현재 선택된 자동결제수단 표시 정보")
    public record CurrentPaymentMethod(
        @Schema(description = "자동결제수단의 공개 UUID", format = "uuid", example = "1f65f0db-1be2-4e89-a947-23a61c65f18b") String paymentMethodId,
        @Schema(description = "카드사명", nullable = true, example = "현대카드") String cardCompany,
        @Schema(description = "고객 표시용 마스킹 카드번호", nullable = true, example = "****-****-****-1234") String maskedCardNumber
    ) {}
    @Schema(description = "설정 변경으로 발생한 환불 결과")
    public record RefundResult(
        @Schema(description = "환불의 공개 UUID", format = "uuid", example = "119ec1f0-1aa5-4a6e-bca1-865d83a23f15") String refundId,
        @Schema(description = "환불 처리 상태") RefundStatus status,
        @Schema(description = "환불 요청 금액(원)", example = "32700") long requestedAmount,
        @Schema(description = "환불 완료 금액(원)", example = "32700") long refundedAmount,
        @Schema(description = "미처리 환불 금액(원)", example = "0") long unprocessedAmount
    ) {}
}
