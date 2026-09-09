package com.chapchap.subscription.domain.address.response;

public record AddressItemResponse(
        String addressId,
        String name,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethod,
        String otherDeliveryRequest,
        boolean isDefault
) {
}
