package com.chapchap.subscription.domain.terms.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 비대면 보관 약관 동의 요청")
public record TermsAgreementRequest(

        @Schema(description = "동의하는 현재 약관 버전", minimum = "1", example = "1")
        @NotNull
        @Min(1)
        Integer version
) {
}
