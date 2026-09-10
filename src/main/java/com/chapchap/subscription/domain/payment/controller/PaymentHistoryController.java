package com.chapchap.subscription.domain.payment.controller;

import com.chapchap.subscription.domain.payment.response.PaymentDetailResponse;
import com.chapchap.subscription.domain.payment.response.PaymentListResponse;
import com.chapchap.subscription.domain.payment.response.RefundDetailResponse;
import com.chapchap.subscription.domain.payment.response.RefundListResponse;
import com.chapchap.subscription.domain.payment.service.PaymentHistoryQueryService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 고객의 결제 거래와 환불 업무 이력을 조회하는 HTTP 진입점이다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
@Tag(name = "결제·환불 내역")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PaymentHistoryController {
    private final PaymentHistoryQueryService paymentHistoryQueryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payments")
    @Operation(summary = "결제 내역 목록 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<PaymentListResponse> getPayments(Authentication authentication) {
        return GlobalResponse.success(
            paymentHistoryQueryService.getPayments(Long.parseLong(authentication.getName()))
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "결제 내역 상세 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.PAYMENT_HISTORY_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<PaymentDetailResponse> getPayment(
        Authentication authentication,
        @PathVariable String paymentId
    ) {
        return GlobalResponse.success(
            paymentHistoryQueryService.getPayment(Long.parseLong(authentication.getName()), paymentId)
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/refunds")
    @Operation(summary = "환불 내역 목록 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<RefundListResponse> getRefunds(Authentication authentication) {
        return GlobalResponse.success(
            paymentHistoryQueryService.getRefunds(Long.parseLong(authentication.getName()))
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/refunds/{refundId}")
    @Operation(summary = "환불 내역 상세 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.REFUND_HISTORY_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<RefundDetailResponse> getRefund(
        Authentication authentication,
        @PathVariable String refundId
    ) {
        return GlobalResponse.success(
            paymentHistoryQueryService.getRefund(Long.parseLong(authentication.getName()), refundId)
        );
    }
}
