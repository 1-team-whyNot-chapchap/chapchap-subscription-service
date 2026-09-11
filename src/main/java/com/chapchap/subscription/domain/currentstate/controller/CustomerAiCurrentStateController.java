package com.chapchap.subscription.domain.currentstate.controller;

import com.chapchap.subscription.domain.currentstate.response.CurrentPaymentStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentRefundStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentSubscriptionStateResponse;
import com.chapchap.subscription.domain.currentstate.service.CustomerAiCurrentStateQueryService;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer-AI Current-State 내부 조회 API의 HTTP 진입점이다. */
@RestController
@Hidden
@RequiredArgsConstructor
@RequestMapping("/api/subscription/internal/v1/current-state")
public class CustomerAiCurrentStateController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final CustomerAiCurrentStateQueryService queryService;

    @GetMapping("/payment")
    public GlobalResponse<CurrentPaymentStateResponse> getCurrentPayment(
        @RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId
    ) {
        return GlobalResponse.success(queryService.getCurrentPayment(parseUserId(rawUserId)));
    }

    @GetMapping("/refund")
    public GlobalResponse<CurrentRefundStateResponse> getCurrentRefund(
        @RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId
    ) {
        return GlobalResponse.success(queryService.getCurrentRefund(parseUserId(rawUserId)));
    }

    @GetMapping("/subscription")
    public GlobalResponse<CurrentSubscriptionStateResponse> getCurrentSubscription(
        @RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId
    ) {
        return GlobalResponse.success(queryService.getCurrentSubscription(parseUserId(rawUserId)));
    }

    private Long parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank() || !rawUserId.matches("[0-9]+")) {
            throw new IllegalArgumentException("사용자 식별자는 양의 정수 문자열이어야 합니다.");
        }
        try {
            long userId = Long.parseLong(rawUserId);
            if (userId <= 0) {
                throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("사용자 식별자는 Long 범위의 양수여야 합니다.", exception);
        }
    }
}
