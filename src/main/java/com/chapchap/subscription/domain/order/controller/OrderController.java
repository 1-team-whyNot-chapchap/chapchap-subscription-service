package com.chapchap.subscription.domain.order.controller;

import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderListResponse;
import com.chapchap.subscription.domain.order.service.OrderQueryService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 고객의 주문 목록과 상세 HTTP 진입점을 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/orders")
@Tag(name = "주문")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class OrderController {
    private final OrderQueryService orderQueryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    @Operation(summary = "주문 목록 조회", description = "인증 고객의 주문 목록을 배송일 기준으로 조회합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<OrderListResponse> getOrders(Authentication authentication) {
        return GlobalResponse.success(
            orderQueryService.getOrders(Long.parseLong(authentication.getName()))
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{orderId}")
    @Operation(summary = "주문 상세 조회", description = "주문 당시 확정된 메뉴·배송지·금액 스냅샷과 환불 상태를 조회합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.ORDER_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<OrderDetailResponse> getOrder(
        Authentication authentication,
        @Parameter(description = "조회할 주문의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String orderId
    ) {
        return GlobalResponse.success(
            orderQueryService.getOrder(Long.parseLong(authentication.getName()), orderId)
        );
    }
}
