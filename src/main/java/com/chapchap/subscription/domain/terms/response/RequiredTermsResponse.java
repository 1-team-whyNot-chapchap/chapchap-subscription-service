package com.chapchap.subscription.domain.terms.response;

public record RequiredTermsResponse(
        String termsType,
        String title,
        String content,
        Integer version
) {
}
