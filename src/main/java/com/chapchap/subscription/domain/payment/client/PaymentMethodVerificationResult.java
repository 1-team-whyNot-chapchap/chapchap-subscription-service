package com.chapchap.subscription.domain.payment.client;

public record PaymentMethodVerificationResult(
    boolean valid
    , String cardCompany
    , String maskedCardNumber
) {
}