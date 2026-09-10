package com.chapchap.subscription.domain.address.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기본 배송지 변경 결과")
public record AddressDefaultResponse(
        @Schema(description = "기본 배송지로 지정된 배송지의 공개 UUID", format = "uuid", example = "8d4c83e5-9f5b-4a7f-9235-9beff28a4a11")
        String addressId,
        @Schema(description = "기본 배송지 여부", example = "true")
        boolean isDefault
) {
}
