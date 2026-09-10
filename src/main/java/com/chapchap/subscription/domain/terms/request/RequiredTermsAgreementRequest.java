package com.chapchap.subscription.domain.terms.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "현재 필수 약관 동의 요청")
public record RequiredTermsAgreementRequest(

        @Schema(description = "동의할 약관 유형", example = "SUBSCRIPTION_SERVICE_TERMS")
        @NotBlank
        @Size(max = 50)
        String termsType,

        @Schema(description = "화면에 표시된 약관 버전", example = "1", minimum = "1")
        @NotNull
        @Min(1)
        Integer version
) {
}
