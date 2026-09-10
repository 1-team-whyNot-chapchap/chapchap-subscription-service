package com.chapchap.subscription.domain.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "필수 약관 동의 처리 결과")
public record RequiredTermsAgreementResponse(
        @Schema(description = "동의한 약관 유형", example = "SUBSCRIPTION_SERVICE_TERMS")
        String termsType,
        @Schema(description = "동의한 약관 버전", example = "1", minimum = "1")
        Integer version,
        @Schema(description = "동의 처리 시각(ISO-8601 OffsetDateTime)", example = "2026-09-10T10:30:00+09:00")
        OffsetDateTime agreedAt
) {
}
