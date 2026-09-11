package com.chapchap.subscription.domain.payment.controller;

import com.chapchap.subscription.domain.payment.response.PaymentMethodListResponse;
import com.chapchap.subscription.domain.payment.service.PaymentMethodService;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.request.PaymentMethodCreateRequest;
import com.chapchap.subscription.domain.payment.response.PaymentMethodCreateResponse;
import com.chapchap.subscription.domain.payment.response.PaymentMethodCurrentResponse;
import com.chapchap.subscription.domain.payment.response.PaymentMethodDeleteResponse;
import com.chapchap.subscription.global.response.GlobalResponse;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/payment-methods")
@Tag(name = "자동결제수단")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @Operation(summary = "자동결제수단 등록", description = "PortOne에서 발급된 빌링키를 서버에 등록합니다. 빌링키 원문은 응답·문서에 반환하지 않습니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.PAYMENT_METHOD_INVALID,
        ErrorCode.PAYMENT_PROVIDER_AUTHENTICATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_CONFLICT,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<PaymentMethodCreateResponse>> create(
        @Valid @RequestBody PaymentMethodCreateRequest request
        , Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        PaymentMethod paymentMethod = paymentMethodService.registerPaymentMethod(userId, request.billingKey());
        PaymentMethodCreateResponse response = PaymentMethodCreateResponse.from(paymentMethod);

        return ResponseEntity.ok(GlobalResponse.success(response));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    @Operation(summary = "사용 가능한 자동결제수단 목록 조회", description = "인증 고객이 선택할 수 있는 AVAILABLE 상태의 자동결제수단만 조회합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<PaymentMethodListResponse>> getPaymentMethods(
        Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<PaymentMethod> paymentMethods = paymentMethodService.getAvailablePaymentMethods(userId);

        PaymentMethodListResponse response = PaymentMethodListResponse.from(paymentMethods);

        return ResponseEntity.ok(GlobalResponse.success(response));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{paymentMethodId}/current")
    @Operation(summary = "현재 자동결제수단 선택", description = "다음 자동결제에 사용할 수단을 변경합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.PAYMENT_METHOD_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<PaymentMethodCurrentResponse>> selectCurrentPaymentMethod(
        @Parameter(description = "현재 수단으로 선택할 자동결제수단의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String paymentMethodId
        , Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        PaymentMethod paymentMethod = paymentMethodService.selectCurrentPaymentMethod(userId, paymentMethodId);
        PaymentMethodCurrentResponse response = PaymentMethodCurrentResponse.from(paymentMethod);

        return ResponseEntity.ok(GlobalResponse.success(response));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{paymentMethodId}")
    @Operation(summary = "자동결제수단 삭제", description = "현재 수단이 아닌 AVAILABLE 자동결제수단을 삭제합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.PAYMENT_METHOD_NOT_FOUND,
        ErrorCode.CURRENT_PAYMENT_METHOD_DELETE_NOT_ALLOWED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<PaymentMethodDeleteResponse>> deletePaymentMethod(
            @Parameter(description = "삭제할 자동결제수단의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String paymentMethodId
            , Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        PaymentMethod paymentMethod = paymentMethodService.deletePaymentMethod(userId, paymentMethodId);
        PaymentMethodDeleteResponse response = PaymentMethodDeleteResponse.from(paymentMethod);

        return ResponseEntity.ok(GlobalResponse.success(response));
    }
}
