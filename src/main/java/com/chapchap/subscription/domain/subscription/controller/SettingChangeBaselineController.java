package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.response.SettingChangeBaselineResponse;
import com.chapchap.subscription.domain.subscription.service.SettingChangeBaselineQueryService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "구독")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SettingChangeBaselineController {
    private final SettingChangeBaselineQueryService queryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/subscription/subscriptions/setting-changes/baseline")
    @Operation(summary = "구독 설정 변경 기준 조회", description = "변경 적용 예정일에 유효한 확정 설정을 조회합니다. 저장·결제·환불·Kafka 발행은 수행하지 않습니다.")
    @CustomApiResponse({ErrorCode.AUTHENTICATION_REQUIRED, ErrorCode.SUBSCRIPTION_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_CHANGE_NOT_ALLOWED, ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS,
        ErrorCode.DATABASE_ERROR, ErrorCode.INTERNAL_SERVER_ERROR})
    public GlobalResponse<SettingChangeBaselineResponse> getBaseline(Authentication authentication) {
        return GlobalResponse.success(queryService.getBaseline(Long.parseLong(authentication.getName())));
    }
}
