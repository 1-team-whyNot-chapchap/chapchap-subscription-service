package com.chapchap.subscription.domain.address.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "배송지 목록 항목")
public record AddressItemResponse(
        @Schema(description = "배송지의 공개 UUID", format = "uuid", example = "8d4c83e5-9f5b-4a7f-9235-9beff28a4a11")
        String addressId,
        @Schema(description = "고객이 지정한 배송지 별칭", example = "회사")
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
        String addressLine2,
        @Schema(description = "배송 방식", allowableValues = {"DIRECT", "DOORSTEP", "OTHER"}, example = "DOORSTEP")
        String deliveryMethod,
        @Schema(description = "직접 입력 배송 요청", nullable = true, example = "경비실에 맡겨 주세요")
        String otherDeliveryRequest,
        @Schema(description = "기본 배송지 여부", example = "true")
        boolean isDefault
) {
}
