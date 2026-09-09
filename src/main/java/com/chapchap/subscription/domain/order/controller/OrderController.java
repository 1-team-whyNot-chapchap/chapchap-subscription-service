package com.chapchap.subscription.domain.order.controller;

import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderListResponse;
import com.chapchap.subscription.domain.order.service.OrderQueryService;
import com.chapchap.subscription.global.response.GlobalResponse;
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
public class OrderController {
    private final OrderQueryService orderQueryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public GlobalResponse<OrderListResponse> getOrders(Authentication authentication) {
        return GlobalResponse.success(
            orderQueryService.getOrders(Long.parseLong(authentication.getName()))
        );
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{orderId}")
    public GlobalResponse<OrderDetailResponse> getOrder(
        Authentication authentication,
        @PathVariable String orderId
    ) {
        return GlobalResponse.success(
            orderQueryService.getOrder(Long.parseLong(authentication.getName()), orderId)
        );
    }
}
