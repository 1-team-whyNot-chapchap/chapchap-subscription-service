package com.chapchap.subscription.domain.address.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "인증 고객의 배송지 목록")
public record AddressListResponse(
        @Schema(description = "삭제되지 않은 배송지 목록")
        List<AddressItemResponse> addresses
) {
}
