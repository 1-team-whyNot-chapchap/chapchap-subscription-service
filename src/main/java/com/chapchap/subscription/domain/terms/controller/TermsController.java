package com.chapchap.subscription.domain.terms.controller;

import com.chapchap.subscription.domain.terms.request.TermsAgreementRequest;
import com.chapchap.subscription.domain.terms.response.TermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.TermsCurrentResponse;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.response.GlobalResponse;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.config.openapi.OpenApiConfig;
import com.chapchap.subscription.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/terms/non-face-to-face")
@Tag(name = "비대면 보관 약관")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TermsController {

    private final TermsService termsService;

    // 현제 적용중인 약관 데이터 받아오기
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    @Operation(summary = "현재 비대면 보관 약관 조회")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<TermsCurrentResponse>> getCurrentTerms() {
        TermsCurrentResponse response =
                termsService.getCurrentTerms();

        return ResponseEntity.ok(
                GlobalResponse.success(response)
        );
    }

    // 동의한 내역이 있는지 확인하고 처리
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/agreements")
    @Operation(summary = "비대면 보관 약관 동의")
    @CustomApiResponse({
        ErrorCode.AUTHENTICATION_REQUIRED,
        ErrorCode.INVALID_REQUEST,
        ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND,
        ErrorCode.TERMS_VERSION_MISMATCH,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public ResponseEntity<GlobalResponse<TermsAgreementResponse>> agreeTerms(
            @Valid @RequestBody TermsAgreementRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());

        TermsAgreementResponse response =
                termsService.agreeTerms(
                        userId,
                        request
                );

        return ResponseEntity.ok(
                GlobalResponse.success(response)
        );
    }
}
