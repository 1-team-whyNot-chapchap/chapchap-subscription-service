package com.chapchap.subscription.domain.payment.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자동결제수단 등록 요청")
public record PaymentMethodCreateRequest(
    @Schema(description = "PortOne에서 발급받은 빌링키. 문서 예시·로그에는 절대 노출하지 않음", writeOnly = true)
    @NotBlank
    String billingKey
) {
}
