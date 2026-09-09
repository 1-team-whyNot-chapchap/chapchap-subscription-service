package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;

public record PaymentMethodDeleteResponse(
    String paymentMethodId
) {

    public static PaymentMethodDeleteResponse from(PaymentMethod paymentMethod) {
        return new PaymentMethodDeleteResponse(paymentMethod.getPublicId());
    }
}
