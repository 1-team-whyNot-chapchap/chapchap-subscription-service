package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;

public record PaymentMethodCurrentResponse(
    String paymentMethodId
    , boolean isCurrent
) {

    public static PaymentMethodCurrentResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodCurrentResponse(
            paymentMethod.getPublicId()
            , paymentMethod.isCurrent()
        );
    }
}
