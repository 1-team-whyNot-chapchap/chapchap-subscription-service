package com.chapchap.subscription.domain.payment.client;

import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomaticPaymentContractTest {
    @Test
    void convertsProviderNeutralRequestToPortOneBody() {
        AutomaticPaymentRequest request = new AutomaticPaymentRequest(
            "external-payment-1",
            "idempotency-key-1",
            "test-external-method-ref",
            "챱챱 첫 구독 결제",
            100_000L,
            "KRW"
        );

        PortOneBillingKeyPaymentRequest body = PortOneBillingKeyPaymentRequest.from(request);

        assertEquals("test-external-method-ref", body.billingKey());
        assertEquals("챱챱 첫 구독 결제", body.orderName());
        assertEquals(100_000L, body.amount().total());
        assertEquals("KRW", body.currency());
    }

    @Test
    void successAndFailureResultsKeepDifferentFields() {
        AutomaticPaymentResult success = AutomaticPaymentResult.success(
            "external-payment-1",
            "external-transaction-1",
            "PAID"
        );
        AutomaticPaymentResult failure = AutomaticPaymentResult.declined(
            "external-payment-2",
            "PAYMENT_FAILED",
            "카드 승인이 거절되었습니다."
        );

        assertNull(success.failureReason());
        assertNull(failure.externalTransactionRef());

        AutomaticPaymentResult configurationFailure =
            AutomaticPaymentResult.providerConfigurationFailed(
                "external-payment-3",
                "CHANNELNOTFOUND",
                "외부 결제 연동 설정 오류가 발생했습니다."
            );
        assertEquals(
            AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED,
            configurationFailure.status()
        );
        assertNull(configurationFailure.externalTransactionRef());
    }

    @Test
    void nonPositivePaymentAmountIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new AutomaticPaymentRequest(
                "external-payment-1",
                "idempotency-key-1",
                "test-external-method-ref",
                "챱챱 첫 구독 결제",
                0L,
                "KRW"
            )
        );
    }

    @Test
    void sensitiveMethodReferenceIsRedactedFromStringRepresentations() {
        AutomaticPaymentRequest request = new AutomaticPaymentRequest(
            "external-payment-1",
            "idempotency-key-1",
            "test-sensitive-method-reference",
            "챱챱 첫 구독 결제",
            100_000L,
            "KRW"
        );
        PortOneBillingKeyPaymentRequest body = PortOneBillingKeyPaymentRequest.from(request);

        org.junit.jupiter.api.Assertions.assertFalse(request.toString().contains("test-sensitive-method-reference"));
        org.junit.jupiter.api.Assertions.assertFalse(body.toString().contains("test-sensitive-method-reference"));
    }

    @Test
    void idempotencyKeyAndProviderFailureReasonAreRedactedFromStringRepresentations() {
        String idempotencyKey = "idempotency-sensitive-key";
        String failureReason = "provider-sensitive-failure-reason";
        AutomaticPaymentRequest request = new AutomaticPaymentRequest(
            "external-payment-1",
            idempotencyKey,
            "test-sensitive-method-reference",
            "챱챱 첫 구독 결제",
            100_000L,
            "KRW"
        );
        AutomaticPaymentResult providerResult = AutomaticPaymentResult.declined(
            "external-payment-1",
            "DECLINED",
            failureReason
        );
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 3, 20, 0);
        FirstPaymentExecutionResult executionResult = new FirstPaymentExecutionResult(
            1L,
            2L,
            PaymentProviderCode.PORTONE,
            idempotencyKey,
            100_000L,
            requestedAt,
            requestedAt.plusSeconds(1),
            providerResult
        );

        org.junit.jupiter.api.Assertions.assertFalse(request.toString().contains(idempotencyKey));
        org.junit.jupiter.api.Assertions.assertFalse(providerResult.toString().contains(failureReason));
        org.junit.jupiter.api.Assertions.assertFalse(executionResult.toString().contains(idempotencyKey));
        org.junit.jupiter.api.Assertions.assertFalse(executionResult.toString().contains(failureReason));
    }
}
