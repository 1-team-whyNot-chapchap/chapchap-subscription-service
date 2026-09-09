package com.chapchap.subscription.domain.terms.response;

public record TermsCurrentResponse(
        String title,
        String content,
        Integer version
) {
}
