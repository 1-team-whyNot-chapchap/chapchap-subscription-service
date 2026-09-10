package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import com.chapchap.subscription.domain.subscription.response.CurrentSubscriptionResponse;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionPreviewResponse;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionResponse;
import com.chapchap.subscription.domain.subscription.response.SettingChangePreviewResponse;
import com.chapchap.subscription.domain.subscription.response.SubscriptionCancellationResponse;
import com.chapchap.subscription.domain.subscription.response.SettingChangeResponse;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationService;
import com.chapchap.subscription.domain.subscription.service.CurrentSubscriptionQueryService;
import com.chapchap.subscription.domain.subscription.service.FirstSubscriptionService;
import com.chapchap.subscription.domain.subscription.service.FirstSubscriptionPreparationService;
import com.chapchap.subscription.domain.subscription.service.SettingChangeService;
import com.chapchap.subscription.domain.subscription.service.SettingChangePreviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 고객의 첫 구독 신청과 첫 결제 HTTP 진입점을 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/subscriptions")
@Tag(name = "구독")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SubscriptionController {
    private final FirstSubscriptionService firstSubscriptionService;
    private final FirstSubscriptionPreparationService firstSubscriptionPreparationService;
    private final CurrentSubscriptionQueryService currentSubscriptionQueryService;
    private final SubscriptionCancellationService subscriptionCancellationService;
    private final SettingChangeService settingChangeService;
    private final SettingChangePreviewService settingChangePreviewService;

    /** Gateway 인증 고객의 현재 구독 상태와 적용 설정을 조회한다. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    @Operation(summary = "현재 구독 현황 조회", description = "인증 고객의 현재 구독 상태와 적용 중인 플랜·요일별 배송 조건을 조회합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<CurrentSubscriptionResponse> getCurrentSubscription(
        Authentication authentication
    ) {
        return GlobalResponse.success(
            currentSubscriptionQueryService.getCurrentSubscription(
                Long.parseLong(authentication.getName())
            )
        );
    }

    /** Gateway 인증 고객을 기준으로 첫 구독 신청을 처리한다. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @Operation(summary = "첫 구독 신청 및 첫 결제", description = "현재 약관 동의와 결제수단을 확인한 뒤 첫 구독을 생성하고 자동결제를 요청합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.TERMS_AGREEMENT_REQUIRED,
        ErrorCode.PLAN_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.CURRENT_PAYMENT_METHOD_REQUIRED,
        ErrorCode.PAYMENT_DECLINED,
        ErrorCode.PAYMENT_PROVIDER_AUTHENTICATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<FirstSubscriptionResponse> create(
        Authentication authentication,
        @Valid @RequestBody FirstSubscriptionRequest request
    ) {
        return GlobalResponse.success(
            firstSubscriptionService.subscribe(Long.parseLong(authentication.getName()), request)
        );
    }

    /** 실제 첫 결제 전에 인증 고객의 신청 조건으로 예상 결제금액을 조회한다. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/preview")
    @Operation(summary = "첫 구독 예상 결제금액 조회", description = "저장·외부 결제 호출 없이 신청 조건의 예상 이용 기간과 결제금액을 계산합니다. 실제 결제는 별도 신청 요청에서 다시 계산합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.TERMS_AGREEMENT_REQUIRED,
        ErrorCode.PLAN_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.CURRENT_PAYMENT_METHOD_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<FirstSubscriptionPreviewResponse> preview(
        Authentication authentication,
        @Valid @RequestBody FirstSubscriptionRequest request
    ) {
        return GlobalResponse.success(
            firstSubscriptionPreparationService.preview(Long.parseLong(authentication.getName()), request)
        );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/setting-changes/preview")
    @Operation(summary = "구독 설정 변경 미리보기", description = "저장·결제·환불·Kafka 호출 없이 변경 후 금액, 적용일, 최종 확인 뒤 필요한 처리를 계산합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.SUBSCRIPTION_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_CHANGE_NOT_ALLOWED,
        ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS,
        ErrorCode.PLAN_NOT_FOUND,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.TERMS_AGREEMENT_REQUIRED,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<SettingChangePreviewResponse> previewSettingChange(
        Authentication authentication, @Valid @RequestBody SettingChangeRequest request
    ) {
        return GlobalResponse.success(settingChangePreviewService.preview(
            Long.parseLong(authentication.getName()), request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/setting-changes")
    @Operation(summary = "구독 설정 변경 확인 및 실행", description = "미리보기에서 확인한 변경 조건을 최신 상태로 다시 검증·계산한 뒤, 차액 없음·추가 결제·환불을 처리하고 완료 결과를 반환합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.SUBSCRIPTION_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_CHANGE_NOT_ALLOWED,
        ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS,
        ErrorCode.PLAN_NOT_FOUND,
        ErrorCode.ADDRESS_NOT_FOUND,
        ErrorCode.CURRENT_PAYMENT_METHOD_REQUIRED,
        ErrorCode.SETTING_CHANGE_PAYMENT_DECLINED,
        ErrorCode.PAYMENT_CANCELLATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_AUTHENTICATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<SettingChangeResponse> changeSetting(
        Authentication authentication, @Valid @RequestBody SettingChangeRequest request
    ) {
        return GlobalResponse.success(settingChangeService.change(
            Long.parseLong(authentication.getName()), request));
    }

    /** 현재 상태에 맞는 일반 해지·시작 취소·재시도 중단을 처리한다. */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    @Operation(summary = "구독 해지 또는 시작 전 취소", description = "현재 구독 상태에 맞게 일반 해지, 시작 전 취소 또는 정기결제 재시도 중단을 처리합니다.")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.SUBSCRIPTION_NOT_FOUND,
        ErrorCode.SUBSCRIPTION_CANCELLATION_NOT_ALLOWED,
        ErrorCode.SUBSCRIPTION_PRE_START_CANCELLATION_DEADLINE_PASSED,
        ErrorCode.SUBSCRIPTION_KAFKA_DELIVERY_COMPLETED,
        ErrorCode.PAYMENT_TRANSACTION_PROCESSING,
        ErrorCode.PAYMENT_CANCELLATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_AUTHENTICATION_FAILED,
        ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<SubscriptionCancellationResponse> cancel(Authentication authentication) {
        return GlobalResponse.success(
            subscriptionCancellationService.cancel(Long.parseLong(authentication.getName()))
        );
    }
}
