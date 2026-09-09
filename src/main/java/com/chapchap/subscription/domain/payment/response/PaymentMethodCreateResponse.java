package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;

public record PaymentMethodCreateResponse(
    String paymentMethodId
    , String cardCompany
    , String maskedCardNumber
    , boolean isCurrent
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
