package com.chapchap.subscription.domain.address.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressCreateRequest(

        @NotBlank
        @Size(max = 50)
        String name,

        @NotBlank
        @Size(max = 50)
        String recipientName,

        @NotBlank
        @Size(max = 20)
        String recipientPhone,

        @NotBlank
        @Size(max = 10)
        String postalCode,

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank
        @Size(max = 20)
        String deliveryMethod,

        @Size(max = 255)
        String otherDeliveryRequest,

        @Size(max = 100)
        String entrancePassword
) {
}
