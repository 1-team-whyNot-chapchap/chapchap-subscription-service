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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Operation(summary = "배송지 목록 조회", description = "인증 고객이 삭제하지 않은 배송지와 현재 기본 배송지 여부를 조회합니다.")
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
    @Operation(summary = "배송지 등록", description = "인증 고객의 배송지를 등록합니다. 첫 배송지는 기본 배송지로 등록됩니다.")
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
    @Operation(summary = "배송지 수정", description = "요청 본문에 포함한 필드만 수정합니다. null로 보낸 선택 필드는 기존 값을 지웁니다.")
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
            @Parameter(description = "수정할 배송지의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
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
    @Operation(summary = "기본 배송지 설정", description = "인증 고객의 활성 배송지 중 하나를 기본 배송지로 지정합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<AddressDefaultResponse> setDefaultAddress(
            Authentication authentication,
            @Parameter(description = "기본 배송지로 지정할 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
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
    @Operation(summary = "배송지 삭제", description = "사용 중이지 않은 배송지를 소프트 삭제합니다.")
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
            @Parameter(description = "삭제할 배송지의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
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
