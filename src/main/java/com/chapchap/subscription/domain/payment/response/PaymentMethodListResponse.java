package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;

import java.util.List;

public record PaymentMethodListResponse(
    List<PaymentMethodItem> paymentMethods
) {
    public static PaymentMethodListResponse from(List<PaymentMethod> paymentMethods) {
        return new PaymentMethodListResponse(
            paymentMethods.stream()
                    .map(PaymentMethodItem::from)
                    .toList()
        );
    }

    public record PaymentMethodItem(
        String paymentMethodId
        , String cardCompany
        , String maskedCardNumber
        , boolean isCurrent
    ) {
        private static PaymentMethodItem from(PaymentMethod paymentMethod) {
            return new PaymentMethodItem(
                    paymentMethod.getPublicId()
                    , paymentMethod.getCardCompany()
                    , paymentMethod.getMaskedCardNumber()
                    , paymentMethod.isCurrent()
            );
        }
    }
}