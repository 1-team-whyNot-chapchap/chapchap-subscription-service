package com.chapchap.subscription.domain.address.controller;

import com.chapchap.subscription.domain.address.request.AddressCreateRequest;
import com.chapchap.subscription.domain.address.request.AddressUpdateRequest;
import com.chapchap.subscription.domain.address.response.*;
import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/addresses")
@Tag(name = "배송지")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AddressController {

    private final AddressService addressService;

    // 배송지 조회
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    @Operation(summary = "배송지 목록 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
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
    @Operation(summary = "배송지 등록")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.ADDRESS_OUT_OF_SERVICE_AREA,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
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
    @Operation(summary = "배송지 수정")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.ADDRESS_OUT_OF_SERVICE_AREA,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
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
    @Operation(summary = "기본 배송지 설정")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
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
    @Operation(summary = "배송지 삭제")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.DEFAULT_ADDRESS_DELETE_NOT_ALLOWED,
        ErrorCode.ADDRESS_IN_USE,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
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
