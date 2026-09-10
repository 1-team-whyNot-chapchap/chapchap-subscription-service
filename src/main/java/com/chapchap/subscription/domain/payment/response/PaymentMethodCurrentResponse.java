package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 자동결제수단 선택 결과")
public record PaymentMethodCurrentResponse(
    @Schema(description = "현재 자동결제수단으로 선택된 공개 UUID", format = "uuid", example = "1f65f0db-1be2-4e89-a947-23a61c65f18b")
    String paymentMethodId
    , @Schema(description = "현재 자동결제수단 여부", example = "true")
    boolean isCurrent
) {

    public static PaymentMethodCurrentResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodCurrentResponse(
            paymentMethod.getPublicId()
            , paymentMethod.isCurrent()
        );
    }
}
