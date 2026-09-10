package com.chapchap.subscription.domain.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

// OffsetDateTime: DB에서 DATETIME(6) ↔ LocalDateTime 데이터타입을 명시,
// 그러나 HTTP API(프론트에서 데이터를 요청함)에서는 KST 기준 ISO-8601 Offset Date-Time을 요구하므로
@Schema(description = "비대면 보관 약관 동의 결과")
public record TermsAgreementResponse(
        @Schema(description = "동의한 약관 버전", example = "1", minimum = "1")
        Integer version,
        @Schema(description = "약관 동의 처리 시각(KST ISO-8601 offset date-time)", example = "2026-09-10T15:30:00+09:00")
        OffsetDateTime agreedAt
) {
}
