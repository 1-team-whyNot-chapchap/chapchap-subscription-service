package com.chapchap.subscription.domain.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 필수 약관")
public record RequiredTermsResponse(
        @Schema(description = "약관 유형", example = "SUBSCRIPTION_SERVICE_TERMS")
        String termsType,
        @Schema(description = "약관 제목", example = "구독 서비스 이용약관")
        String title,
        @Schema(description = "약관 전문")
        String content,
        @Schema(description = "현재 약관 버전", example = "1", minimum = "1")
        Integer version
) {
}
