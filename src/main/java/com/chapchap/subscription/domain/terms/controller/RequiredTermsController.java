package com.chapchap.subscription.domain.terms.controller;

import com.chapchap.subscription.domain.terms.request.RequiredTermsAgreementRequest;
import com.chapchap.subscription.domain.terms.response.RequiredTermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.RequiredTermsResponse;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 첫 구독 계약 전에 확인·동의해야 하는 현재 필수 약관 API다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/terms")
@Tag(name = "구독 필수 약관")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RequiredTermsController {

    private final TermsService termsService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/required")
    @Operation(summary = "현재 필수 약관 전체 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<List<RequiredTermsResponse>>> getCurrentRequiredTerms() {
        return ResponseEntity.ok(GlobalResponse.success(termsService.getCurrentRequiredTerms()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/agreements")
    @Operation(summary = "현재 필수 약관 동의")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.TERMS_VERSION_MISMATCH,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<RequiredTermsAgreementResponse>> agreeRequiredTerms(
            @Valid @RequestBody RequiredTermsAgreementRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(GlobalResponse.success(termsService.agreeRequiredTerms(userId, request)));
    }
}
