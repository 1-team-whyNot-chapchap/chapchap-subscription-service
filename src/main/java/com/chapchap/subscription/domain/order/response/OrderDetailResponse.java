package com.chapchap.subscription.domain.order.response;

import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 주문 당시 스냅샷과 고정 메뉴 안내를 함께 제공하는 주문 상세 응답이다. */
@Schema(description = "주문 당시 스냅샷과 고정 메뉴 안내")
public record OrderDetailResponse(
    @Schema(description = "주문의 공개 UUID", format = "uuid", example = "7834a801-703d-4d3b-b08e-109a898d999b")
    String orderId,
    @Schema(description = "배송 예정일", example = "2026-09-14")
    LocalDate deliveryDate,
    @Schema(description = "주문 상태")
    OrderStatus status,
    @Schema(description = "주문 당시 플랜명", example = "간편식")
    String planName,
    @Schema(description = "주문 당시 메뉴명", example = "닭가슴살 샐러드")
    String menuName,
    @Schema(description = "주문 식사 수량", example = "3")
    Integer mealQuantity,
    @Schema(description = "주문 당시 메뉴 설명", nullable = true, example = "담백한 닭가슴살과 신선한 채소")
    String menuDescription,
    @Schema(description = "고객 표시용 메뉴 이미지 URL", nullable = true, example = "https://cdn.example.com/menus/meal-001.jpg")
    String imageUrl,
    @Schema(description = "알레르기 유발 정보", nullable = true, example = "대두, 우유")
    String allergenInfo,
    @Schema(description = "영양 정보", nullable = true, example = "열량 450kcal")
    String nutritionInfo,
    @Schema(description = "원재료 정보", nullable = true, example = "닭가슴살, 현미, 채소")
    String ingredientInfo,
    @Schema(description = "수령인 이름", example = "홍길동")
    String recipientName,
    @Schema(description = "수령인 연락처", example = "010-1234-5678")
    String recipientPhone,
    @Schema(description = "우편번호", example = "06236")
    String postalCode,
    @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
    String addressLine1,
    @Schema(description = "상세 주소", nullable = true, example = "101동 1001호")
    String addressLine2,
    @Schema(description = "배송 방식 코드", example = "DOORSTEP")
    String deliveryMethodCode,
    @Schema(description = "직접 입력 배송 요청", nullable = true, example = "경비실에 맡겨 주세요")
    String otherDeliveryRequest,
    @Schema(description = "배송 시간대")
    OrderDeliveryTimeSlot deliveryTimeSlot,
    @Schema(description = "주문 금액(원)", example = "32700")
    Long amount,
    @Schema(description = "주문에 연결된 환불 요약. 환불이 없으면 null", nullable = true)
    RefundResponse refund
) {
    @Schema(description = "주문에 연결된 환불 요약")
    public record RefundResponse(
        @Schema(description = "환불의 공개 UUID", format = "uuid", example = "119ec1f0-1aa5-4a6e-bca1-865d83a23f15")
        String refundId,
        @Schema(description = "환불 처리 상태")
        RefundStatus status,
        @Schema(description = "환불 요청 금액(원)", example = "32700")
        Long requestedAmount,
        @Schema(description = "환불 완료 금액(원)", example = "32700")
        Long refundedAmount,
        @Schema(description = "미처리 환불 금액(원)", example = "0")
        Long unprocessedAmount
    ) {
    }
}
