package com.chapchap.subscription.domain.terms.response;

import java.time.OffsetDateTime;

// OffsetDateTime: DB에서 DATETIME(6) ↔ LocalDateTime 데이터타입을 명시,
    // 그러나 HTTP API(프론트에서 데이터를 요청함)에서는 KST 기준 ISO-8601 Offset Date-Time을 요구하므로
public record TermsAgreementResponse(
        Integer version,
        OffsetDateTime agreedAt
) {
}