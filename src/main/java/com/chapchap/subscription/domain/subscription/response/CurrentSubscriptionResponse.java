package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 인증 고객의 현재 구독 상태와 현재 적용 설정을 제공한다. */
@Schema(description = "인증 고객의 현재 구독과 적용 중인 설정")
public record CurrentSubscriptionResponse(
        @Schema(description = "구독의 공개 UUID", format = "uuid", example = "4bb90ac5-9e29-446e-aee6-26b7c8780ca2")
        String subscriptionId,
        @Schema(description = "현재 구독 상태")
        SubscriptionStatus subscriptionStatus,
        @Schema(description = "현재 이용 기간 시작일", example = "2026-09-14")
        LocalDate periodStartDate,
        @Schema(description = "현재 이용 기간 종료일", example = "2026-10-11")
        LocalDate periodEndDate,
        @Schema(description = "구독 해지 요청 시각. 해지 요청이 없으면 null", nullable = true, example = "2026-09-20T10:00:00")
        LocalDateTime cancellationRequestedAt,
        @Schema(description = "현재 적용 플랜")
        PlanResponse plan,
        @Schema(description = "현재 적용 배송 조건 목록")
        List<DeliveryConditionResponse> deliveryConditions
) {
    public CurrentSubscriptionResponse {
        deliveryConditions = List.copyOf(deliveryConditions);
    }

    @Schema(description = "현재 적용 플랜 정보")
    public record PlanResponse(
            @Schema(description = "플랜의 공개 UUID", format = "uuid", example = "e68fd1c3-bcad-4b19-a417-c7c067a6062a")
            String planId,
            @Schema(description = "플랜명", example = "간편식")
            String name,
            @Schema(description = "플랜 설명", example = "간편하게 즐기는 한 끼 도시락")
            String description,
            @Schema(description = "1인 1식 기준 단가(원)", example = "10900")
            Long unitPrice
    ) {
    }

    @Schema(description = "현재 적용 배송 조건")
    public record DeliveryConditionResponse(
            @Schema(description = "배송 요일")
            DeliveryWeekday weekday,
            @Schema(description = "해당 요일의 식사 인원수", example = "2", minimum = "1", maximum = "6")
            Integer mealQuantity,
            @Schema(description = "배송 시간대")
            DeliveryTimeSlot deliveryTimeSlot,
            @Schema(description = "배송지 정보")
            AddressResponse address
    ) {
    }

    @Schema(description = "구독 배송지 정보. 공동현관 비밀번호는 반환하지 않는다.")
    public record AddressResponse(
            @Schema(description = "배송지의 공개 UUID", format = "uuid", example = "8d4c83e5-9f5b-4a7f-9235-9beff28a4a11")
            String addressId,
            @Schema(description = "배송지 별칭", example = "회사")
            String name,
            @Schema(description = "수령인 이름", example = "홍길동")
            String recipientName,
            @Schema(description = "수령인 연락처", example = "010-1234-5678")
            String recipientPhone,
            @Schema(description = "우편번호", example = "06236")
            String postalCode,
            @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
            String addressLine1,
            @Schema(description = "상세 주소", nullable = true, example = "101동 1001호")
            String addressLine2
    ) {
    }
}
