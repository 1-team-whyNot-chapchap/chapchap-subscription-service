package com.chapchap.subscription.domain.address.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "배송지 수정 결과")
public record AddressUpdateResponse(
        @Schema(description = "수정된 배송지의 공개 UUID", format = "uuid", example = "8d4c83e5-9f5b-4a7f-9235-9beff28a4a11")
        String addressId
) {
}
