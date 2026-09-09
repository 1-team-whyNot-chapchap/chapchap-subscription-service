package com.chapchap.subscription.domain.payment.controller;

import com.chapchap.subscription.domain.payment.response.PaymentMethodListResponse;
import com.chapchap.subscription.domain.payment.service.PaymentMethodService;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.request.PaymentMethodCreateRequest;
import com.chapchap.subscription.domain.payment.response.PaymentMethodCreateResponse;
import com.chapchap.subscription.domain.payment.response.PaymentMethodCurrentResponse;
import com.chapchap.subscription.domain.payment.response.PaymentMethodDeleteResponse;
import com.chapchap.subscription.global.response.GlobalResponse;
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
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
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
    public ResponseEntity<GlobalResponse<PaymentMethodCurrentResponse>> selectCurrentPaymentMethod(
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
    public ResponseEntity<GlobalResponse<PaymentMethodDeleteResponse>> deletePaymentMethod(
            @PathVariable String paymentMethodId
            , Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        PaymentMethod paymentMethod = paymentMethodService.deletePaymentMethod(userId, paymentMethodId);
        PaymentMethodDeleteResponse response = PaymentMethodDeleteResponse.from(paymentMethod);

        return ResponseEntity.ok(GlobalResponse.success(response));
    }
}
