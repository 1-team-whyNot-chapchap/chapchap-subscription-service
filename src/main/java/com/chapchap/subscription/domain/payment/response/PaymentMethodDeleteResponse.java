package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자동결제수단 삭제 결과")
public record PaymentMethodDeleteResponse(
    @Schema(description = "삭제된 자동결제수단의 공개 UUID", format = "uuid", example = "1f65f0db-1be2-4e89-a947-23a61c65f18b")
    String paymentMethodId
) {

    public static PaymentMethodDeleteResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodDeleteResponse(paymentMethod.getPublicId());
    }
}
