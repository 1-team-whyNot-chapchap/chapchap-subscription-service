package com.chapchap.subscription.domain.address.response;

import java.util.List;

public record AddressListResponse(
        List<AddressItemResponse> addresses
) {
}