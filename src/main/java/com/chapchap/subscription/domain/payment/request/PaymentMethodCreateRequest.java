package com.chapchap.subscription.domain.payment.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentMethodCreateRequest(
    @NotBlank
    String billingKey
) {
}
