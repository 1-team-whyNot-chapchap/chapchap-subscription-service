package com.chapchap.subscription.domain.terms.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredTermsAgreementRequest(

        @NotBlank
        @Size(max = 50)
        String termsType,

        @NotNull
        @Min(1)
        Integer version
) {
}
