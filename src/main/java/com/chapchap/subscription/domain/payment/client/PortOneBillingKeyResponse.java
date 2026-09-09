package com.chapchap.subscription.domain.payment.client;

import java.util.List;

public record PortOneBillingKeyResponse(
    String status
    , List<Method> methods
) {
    public record Method(
        String type
        , Card card
    ) {}

    public record Card(
        String name
        , String number
    ) {}
}