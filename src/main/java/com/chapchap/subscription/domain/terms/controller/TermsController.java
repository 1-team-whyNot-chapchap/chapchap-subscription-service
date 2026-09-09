package com.chapchap.subscription.domain.terms.controller;

import com.chapchap.subscription.domain.terms.request.TermsAgreementRequest;
import com.chapchap.subscription.domain.terms.response.TermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.TermsCurrentResponse;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.response.GlobalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/terms/non-face-to-face")
public class TermsController {

    private final TermsService termsService;

    // 현제 적용중인 약관 데이터 받아오기
    @PreAuthorize("isAuthenticated()")
    @GetMapping
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
