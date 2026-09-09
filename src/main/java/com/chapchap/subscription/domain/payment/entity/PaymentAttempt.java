package com.chapchap.subscription.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 외부 결제 요청에 대해 실제로 받은 성공 또는 실패 응답 한 건을 보존하는 이력이다.
 *
 * <p>외부 요청 전에는 생성하지 않고 응답을 받은 뒤에만 생성한다. 같은 거래의 재시도는
 * 새 시도 순번과 새 멱등성 키를 사용하며, 과거 시도 행은 수정하거나 삭제하지 않는다.</p>
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "payment_attempts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempts_sequence", columnNames = {"payment_transaction_id", "attempt_sequence"}),
        @UniqueConstraint(name = "uk_payment_attempts_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_payment_attempts_external_payment_id", columnList = "external_payment_id")
    }
)
@Check(name = "ck_payment_attempts_sequence", constraints = "attempt_sequence >= 1")
@Check(name = "ck_payment_attempts_amount", constraints = "requested_amount >= 1")
@Check(name = "ck_payment_attempts_response_time", constraints = "responded_at >= requested_at")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "payment_transaction_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long paymentTransactionId;

    @Column(name = "payment_method_id", columnDefinition = "BIGINT UNSIGNED")
    private Long paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 30)
    private PaymentProviderCode providerCode;

    @Column(name = "attempt_sequence", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer attemptSequence;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "requested_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long requestedAmount;

    @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;

    @Column(name = "responded_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private PaymentAttemptResult result;

    @Column(name = "external_payment_id", length = 255)
    private String externalPaymentId;

    @Column(name = "external_transaction_ref", length = 255)
    private String externalTransactionRef;

    @Column(name = "external_result_code", length = 100)
    private String externalResultCode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    private PaymentAttempt(
        Long paymentTransactionId,
        Long paymentMethodId,
        PaymentProviderCode providerCode,
        Integer attemptSequence,
        String idempotencyKey,
        Long requestedAmount,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        PaymentAttemptResult result,
        String externalPaymentId,
        String externalTransactionRef,
        String externalResultCode,
        String failureReason
    ) {
        this.paymentTransactionId = requirePositive(paymentTransactionId, "paymentTransactionId");
        this.paymentMethodId = paymentMethodId == null ? null : requirePositive(paymentMethodId, "paymentMethodId");
        this.providerCode = requireNonNull(providerCode, "providerCode");
        this.attemptSequence = requirePositive(attemptSequence, "attemptSequence");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestedAmount = requirePositive(requestedAmount, "requestedAmount");
        this.requestedAt = requireNonNull(requestedAt, "requestedAt");
        this.respondedAt = requireNonNull(respondedAt, "respondedAt");
        if (respondedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("respondedAt must not be before requestedAt");
        }
        this.result = requireNonNull(result, "result");
        this.externalPaymentId = externalPaymentId;
        this.externalTransactionRef = externalTransactionRef;
        this.externalResultCode = externalResultCode;
        this.failureReason = failureReason;
        validateResultFields();
    }

    /**
     * 자동결제 성공 응답을 결제 처리 시도 이력으로 생성한다.
     *
     * <p>실제로 사용한 결제수단과 외부 결제·거래 식별정보를 함께 보존하며,
     * 실패 사유는 기록하지 않는다.</p>
     *
     * @param paymentTransactionId 응답을 연결할 내부 결제 거래 식별자
     * @param paymentMethodId 요청에 실제 사용한 자동결제수단 식별자
     * @param providerCode 요청을 처리한 외부 결제 제공자
     * @param attemptSequence 같은 거래 안에서 증가하는 시도 순번
     * @param idempotencyKey 외부 요청 당시 사용한 멱등성 키
     * @param requestedAmount 외부 제공자에 실제 요청한 금액
     * @param requestedAt 외부 요청 전송 시각
     * @param respondedAt 외부 성공 응답 수신 시각
     * @param externalPaymentId 외부 결제 건 식별자
     * @param externalTransactionRef 외부 거래 처리 식별정보
     * @param externalResultCode 외부 제공자가 반환한 성공 결과 코드
     * @return 외부 성공 응답을 보존하는 결제 처리 시도
     * @throws IllegalArgumentException 필수 값이 없거나 응답 시각이 요청 시각보다 이른 경우
     */
    public static PaymentAttempt success(
        Long paymentTransactionId,
        Long paymentMethodId,
        PaymentProviderCode providerCode,
        Integer attemptSequence,
        String idempotencyKey,
        Long requestedAmount,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        String externalPaymentId,
        String externalTransactionRef,
        String externalResultCode
    ) {
        requirePositive(paymentMethodId, "paymentMethodId");
        return new PaymentAttempt(
            paymentTransactionId,
            paymentMethodId,
            providerCode,
            attemptSequence,
            idempotencyKey,
            requestedAmount,
            requestedAt,
            respondedAt,
            PaymentAttemptResult.SUCCESS,
            externalPaymentId,
            externalTransactionRef,
            externalResultCode,
            null
        );
    }

    /**
     * 자동결제 실패 응답을 결제 처리 시도 이력으로 생성한다.
     *
     * <p>외부 거래 식별정보는 성공으로 확정되지 않았으므로 기록하지 않고,
     * 운영 진단에 필요한 실패 사유를 보존한다. 호출자는 실패 사유에 Secret이나
     * 결제수단 참조값이 포함되지 않도록 정제해야 한다.</p>
     *
     * @param paymentTransactionId 응답을 연결할 내부 결제 거래 식별자
     * @param paymentMethodId 요청에 실제 사용한 자동결제수단 식별자
     * @param providerCode 요청을 처리한 외부 결제 제공자
     * @param attemptSequence 같은 거래 안에서 증가하는 시도 순번
     * @param idempotencyKey 외부 요청 당시 사용한 멱등성 키
     * @param requestedAmount 외부 제공자에 실제 요청한 금액
     * @param requestedAt 외부 요청 전송 시각
     * @param respondedAt 외부 실패 응답 수신 시각
     * @param externalPaymentId 외부 결제 건 식별자
     * @param externalResultCode 외부 제공자가 반환한 실패 결과 코드
     * @param failureReason Secret과 결제수단 참조값을 제거한 실패 사유
     * @return 외부 실패 응답을 보존하는 결제 처리 시도
     * @throws IllegalArgumentException 실패 사유 등 필수 값이 없거나 응답 시각이 요청 시각보다 이른 경우
     */
    public static PaymentAttempt failure(
        Long paymentTransactionId,
        Long paymentMethodId,
        PaymentProviderCode providerCode,
        Integer attemptSequence,
        String idempotencyKey,
        Long requestedAmount,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        String externalPaymentId,
        String externalResultCode,
        String failureReason
    ) {
        requirePositive(paymentMethodId, "paymentMethodId");
        return new PaymentAttempt(
            paymentTransactionId,
            paymentMethodId,
            providerCode,
            attemptSequence,
            idempotencyKey,
            requestedAmount,
            requestedAt,
            respondedAt,
            PaymentAttemptResult.FAILURE,
            externalPaymentId,
            null,
            externalResultCode,
            failureReason
        );
    }

    public static PaymentAttempt cancellationSuccess(
        Long paymentTransactionId, PaymentProviderCode providerCode, Integer attemptSequence,
        String idempotencyKey, Long requestedAmount, LocalDateTime requestedAt,
        LocalDateTime respondedAt, String externalPaymentId, String externalTransactionRef,
        String externalResultCode
    ) {
        return new PaymentAttempt(
            paymentTransactionId, null, providerCode, attemptSequence, idempotencyKey,
            requestedAmount, requestedAt, respondedAt, PaymentAttemptResult.SUCCESS,
            externalPaymentId, externalTransactionRef, externalResultCode, null
        );
    }

    public static PaymentAttempt cancellationFailure(
        Long paymentTransactionId, PaymentProviderCode providerCode, Integer attemptSequence,
        String idempotencyKey, Long requestedAmount, LocalDateTime requestedAt,
        LocalDateTime respondedAt, String externalPaymentId, String externalResultCode,
        String failureReason
    ) {
        return new PaymentAttempt(
            paymentTransactionId, null, providerCode, attemptSequence, idempotencyKey,
            requestedAmount, requestedAt, respondedAt, PaymentAttemptResult.FAILURE,
            externalPaymentId, null, externalResultCode, failureReason
        );
    }

    private void validateResultFields() {
        if (result == PaymentAttemptResult.SUCCESS) {
            requireText(externalTransactionRef, "externalTransactionRef");
            if (failureReason != null) {
                throw new IllegalArgumentException("failureReason must be null for a successful attempt");
            }
            return;
        }
        requireText(failureReason, "failureReason");
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Integer requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
