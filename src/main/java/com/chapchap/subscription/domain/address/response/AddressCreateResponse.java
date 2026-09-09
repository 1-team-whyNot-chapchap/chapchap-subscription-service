package com.chapchap.subscription.domain.address.response;

public record AddressCreateResponse(
        String addressId,
        boolean isDefault
) {
}
