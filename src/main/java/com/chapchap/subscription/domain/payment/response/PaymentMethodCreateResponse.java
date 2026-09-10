package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자동결제수단 등록 결과")
public record PaymentMethodCreateResponse(
    @Schema(description = "등록된 자동결제수단의 공개 UUID", format = "uuid", example = "1f65f0db-1be2-4e89-a947-23a61c65f18b")
    String paymentMethodId
    , @Schema(description = "카드사명", nullable = true, example = "현대카드")
    String cardCompany
    , @Schema(description = "고객 표시용 마스킹 카드번호", nullable = true, example = "****-****-****-1234")
    String maskedCardNumber
    , @Schema(description = "현재 자동결제수단 여부", example = "true")
    boolean isCurrent
) {

    public static PaymentMethodCreateResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodCreateResponse(
            paymentMethod.getPublicId()
            , paymentMethod.getCardCompany()
            , paymentMethod.getMaskedCardNumber()
            , paymentMethod.isCurrent()
        );
    }
}
