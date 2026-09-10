package com.chapchap.subscription.domain.terms.response;

import java.time.OffsetDateTime;

public record RequiredTermsAgreementResponse(
        String termsType,
        Integer version,
        OffsetDateTime agreedAt
) {
}
