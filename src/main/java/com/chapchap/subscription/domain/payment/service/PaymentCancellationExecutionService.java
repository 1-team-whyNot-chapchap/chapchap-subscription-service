package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationClient;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationRequest;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 처리 중 원 결제 취소 거래를 DB 트랜잭션 밖에서 실행한다. */
@Service
public class PaymentCancellationExecutionService {
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Seoul");
    private final PaymentTransactionRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentCancellationClient client;

    public PaymentCancellationExecutionService(PaymentTransactionRepository payments,
        PaymentAttemptRepository attempts, PaymentCancellationClient client) {
        this.payments = payments;
        this.attempts = attempts;
        this.client = client;
    }

    public PaymentCancellationExecutionResult execute(Long cancellationTransactionId) {
        var cancellation = payments.findById(cancellationTransactionId).orElseThrow();
        if (cancellation.getStatus() != PaymentTransactionStatus.PROCESSING
            || cancellation.getExternalRequestIdempotencyKey() == null) {
            throw new IllegalStateException("Cancellation transaction is not executable");
        }
        var original = payments.findById(cancellation.getOriginalPaymentTransactionId()).orElseThrow();
        var originalAttempt = attempts
            .findTopByPaymentTransactionIdAndResultOrderByAttemptSequenceDesc(
                original.getId(), PaymentAttemptResult.SUCCESS
            ).orElseThrow(() -> new IllegalStateException("Original payment has no successful attempt"));
        if (original.getCancelableAmount() == null
            || original.getCancelableAmount() < cancellation.getTransactionAmount()) {
            throw new IllegalStateException("Original payment cancellation balance is insufficient");
        }
        LocalDateTime requestedAt = LocalDateTime.now(BUSINESS_ZONE_ID);
        var providerResult = client.cancel(new PaymentCancellationRequest(
            originalAttempt.getExternalPaymentId(), cancellation.getExternalRequestIdempotencyKey(),
            cancellation.getTransactionAmount(), original.getCancelableAmount(), cancellationReason(cancellation.getTransactionType())
        ));
        LocalDateTime respondedAt = LocalDateTime.now(BUSINESS_ZONE_ID);
        return new PaymentCancellationExecutionResult(
            cancellation.getId(), originalAttempt.getProviderCode(),
            cancellation.getExternalRequestIdempotencyKey(), cancellation.getTransactionAmount(),
            requestedAt, respondedAt, providerResult
        );
    }

    private String cancellationReason(com.chapchap.subscription.domain.payment.entity.PaymentTransactionType type) {
        return switch (type) {
            case SETTING_CHANGE_PARTIAL_CANCELLATION -> "구독 설정 변경 감액";
            case DELIVERY_PARTIAL_CANCELLATION -> "배송 건 환불";
            case NEXT_PERIOD_FULL_CANCELLATION -> "다음 이용 기간 취소";
            default -> "구독 시작 전 고객 취소";
        };
    }
}
