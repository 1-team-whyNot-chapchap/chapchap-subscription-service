package com.chapchap.subscription.domain.address.controller;

import com.chapchap.subscription.domain.address.request.AddressCreateRequest;
import com.chapchap.subscription.domain.address.request.AddressUpdateRequest;
import com.chapchap.subscription.domain.address.response.*;
import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.global.response.GlobalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/addresses")
public class AddressController {

    private final AddressService addressService;

    // 배송지 조회
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public GlobalResponse<AddressListResponse> getAddresses(
            Authentication authentication
    ) {
        Long userId = getUserId(authentication);

        return GlobalResponse.success(
                addressService.getAddresses(userId)
        );
    }

    // 배송지 등록
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public GlobalResponse<AddressCreateResponse> createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressCreateRequest request
    ) {
        Long userId = getUserId(authentication);

        return GlobalResponse.success(
                addressService.createAddress(userId, request)
        );
    }

    // 배송지 수정
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{addressId}")
    public GlobalResponse<AddressUpdateResponse> updateAddress(
            Authentication authentication,
            @PathVariable String addressId,
            @Valid @RequestBody AddressUpdateRequest request
    ) {
        Long userId = getUserId(authentication);

        return GlobalResponse.success(
                addressService.updateAddress(
                        userId,
                        addressId,
                        request
                )
        );
    }

    // 기본 배송지 설정
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{addressId}/default")
    public GlobalResponse<AddressDefaultResponse> setDefaultAddress(
            Authentication authentication,
            @PathVariable String addressId
    ) {
        Long userId = getUserId(authentication);

        return GlobalResponse.success(
                addressService.setDefaultAddress(
                        userId,
                        addressId
                )
        );
    }

    // 배송지 삭제
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{addressId}")
    public GlobalResponse<AddressDeleteResponse> deleteAddress(
            Authentication authentication,
            @PathVariable String addressId
    ) {
        Long userId = getUserId(authentication);

        return GlobalResponse.success(
                addressService.deleteAddress(
                        userId,
                        addressId
                )
        );
    }

    private Long getUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
