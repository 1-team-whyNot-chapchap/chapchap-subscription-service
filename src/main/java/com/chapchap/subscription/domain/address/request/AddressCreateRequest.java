package com.chapchap.subscription.domain.address.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "배송지 등록 요청")
public record AddressCreateRequest(
    @Schema(description = "고객이 배송지를 구분하는 이름", example = "집")
    @NotBlank @Size(max = 50) String name,

    @Schema(description = "수령인 이름", example = "홍길동")
    @NotBlank @Size(max = 50) String recipientName,

    @Schema(description = "수령인 연락처", example = "01012345678")
    @NotBlank @Size(max = 20) String recipientPhone,

    @Schema(description = "우편번호", example = "41911")
    @NotBlank @Size(max = 10) String postalCode,

    @Schema(description = "기본 주소", example = "대구광역시 중구 국채보상로 123")
    @NotBlank @Size(max = 255) String addressLine1,

    @Schema(description = "상세 주소", example = "101동 1001호")
    @Size(max = 255) String addressLine2,

    @Schema(description = "배달 방식 코드", allowableValues = {"DIRECT", "DOORSTEP", "OTHER"}, example = "DOORSTEP")
    @NotBlank @Size(max = 20) String deliveryMethod,

    @Schema(description = "배달 방식이 OTHER일 때의 고객 직접 입력 요청", example = "경비실에 맡겨주세요")
    @Size(max = 255) String otherDeliveryRequest,

    @Schema(description = "필요한 경우에만 입력하는 공동현관 비밀번호", example = "1234")
    @Size(max = 100) String entrancePassword
) {
}
