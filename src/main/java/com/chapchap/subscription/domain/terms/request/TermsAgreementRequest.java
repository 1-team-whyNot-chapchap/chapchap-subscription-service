package com.chapchap.subscription.domain.terms.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TermsAgreementRequest(

        @NotNull
        @Min(1)
        Integer version
) {
}
