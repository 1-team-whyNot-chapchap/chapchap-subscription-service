package com.chapchap.subscription.domain.payment.entity;

import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 외부 결제 또는 원 결제 취소 한 건의 업무 상태와 금액을 보존하는 결제 거래다.
 *
 * <p>외부 요청을 보내기 전에 {@link PaymentTransactionStatus#PROCESSING} 상태로 생성하고,
 * 외부 응답을 받은 뒤 성공 또는 실패 상태로 확정한다. 외부 요청별 결과 이력은
 * 이 거래가 아니라 {@link PaymentAttempt}에 별도로 누적한다.</p>
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "payment_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_transactions_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_payment_transactions_business_key", columnNames = "business_deduplication_key"),
        @UniqueConstraint(name = "uk_payment_transactions_external_request_key", columnNames = "external_request_idempotency_key")
    },
    indexes = {
        @Index(name = "idx_payment_transactions_user_history", columnList = "user_id, occurred_at DESC, id DESC"),
        @Index(name = "idx_payment_transactions_period", columnList = "subscription_id, subscription_period_id, transaction_type, status, id"),
        @Index(name = "idx_payment_transactions_refund", columnList = "refund_id, status, id"),
        @Index(name = "idx_payment_transactions_original", columnList = "original_payment_transaction_id, status, id")
    }
)
@Check(name = "ck_payment_transactions_amount", constraints = "transaction_amount >= 1")
@Check(name = "ck_payment_transactions_version", constraints = "payment_state_version >= 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {
    private static final String PUBLIC_ID_PREFIX = "PAY-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 40, columnDefinition = "CHAR(40)")
    private String publicId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Column(name = "subscription_period_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionPeriodId;

    @Column(name = "subscription_setting_id", columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionSettingId;

    @Column(name = "refund_id", columnDefinition = "BIGINT UNSIGNED")
    private Long refundId;

    @Column(name = "original_payment_transaction_id", columnDefinition = "BIGINT UNSIGNED")
    private Long originalPaymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private PaymentTransactionType transactionType;

    @Column(name = "original_payment_amount", columnDefinition = "BIGINT UNSIGNED")
    private Long originalPaymentAmount;

    @Column(name = "transaction_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long transactionAmount;

    @Column(name = "cumulative_cancel_amount", columnDefinition = "BIGINT UNSIGNED")
    private Long cumulativeCancelAmount;

    @Column(name = "cancelable_amount", columnDefinition = "BIGINT UNSIGNED")
    private Long cancelableAmount;

    @Column(name = "processing_reference_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime processingReferenceAt;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "setting_effective_date")
    private LocalDate settingEffectiveDate;

    @Column(name = "business_deduplication_key", nullable = false, length = 255)
    private String businessDeduplicationKey;

    @Column(name = "external_request_idempotency_key", length = 255)
    private String externalRequestIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentTransactionStatus status;

    @Column(name = "payment_state_version", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long paymentStateVersion;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    private PaymentTransaction(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long transactionAmount,
        LocalDateTime processingReferenceAt,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String externalRequestIdempotencyKey,
        LocalDateTime occurredAt
    ) {
        this.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        this.userId = requirePositive(userId, "userId");
        this.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        this.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        this.transactionType = PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT;
        this.transactionAmount = requirePositive(transactionAmount, "transactionAmount");
        this.processingReferenceAt = requireNonNull(processingReferenceAt, "processingReferenceAt");
        this.periodStartDate = requireNonNull(periodStartDate, "periodStartDate");
        this.periodEndDate = requireNonNull(periodEndDate, "periodEndDate");
        if (periodEndDate.isBefore(periodStartDate)) {
            throw new IllegalArgumentException("periodEndDate must not be before periodStartDate");
        }
        this.businessDeduplicationKey = PaymentBusinessKeyGenerator.firstPayment(subscriptionPeriodId);
        this.externalRequestIdempotencyKey = requireText(externalRequestIdempotencyKey, "externalRequestIdempotencyKey");
        this.status = PaymentTransactionStatus.PROCESSING;
        this.paymentStateVersion = 0L;
        this.occurredAt = requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * 첫 구독 결제를 위해 외부 요청 전 결제 거래를 생성한다.
     *
     * <p>거래는 처리 중 상태와 상태 변경 순번 0으로 시작한다. 업무 중복 방지 키는
     * 대상 이용 기간 식별자로 만들며, 외부 PG 요청 멱등성 키와는 별도로 관리한다.</p>
     *
     * @param userId 거래 대상 고객의 내부 식별자
     * @param subscriptionId 거래 대상 구독의 내부 식별자
     * @param subscriptionPeriodId 첫 결제 대상 이용 기간의 내부 식별자
     * @param transactionAmount 외부 결제를 요청할 첫 이용 기간 총금액
     * @param processingReferenceAt 기간·주문·금액 계산에 사용한 고정 기준 시각
     * @param periodStartDate 결제 대상 이용 기간 시작일 스냅샷
     * @param periodEndDate 결제 대상 이용 기간 종료일 스냅샷
     * @param externalRequestIdempotencyKey 현재 외부 요청의 멱등성 키
     * @param occurredAt 첫 결제 업무가 발생한 시각
     * @return 외부 요청 전 처리 중 상태로 생성된 첫 결제 거래
     * @throws IllegalArgumentException 필수 식별자·금액·기간·외부 요청 키가 유효하지 않은 경우
     */
    public static PaymentTransaction createFirstSubscriptionPayment(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long transactionAmount,
        LocalDateTime processingReferenceAt,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String externalRequestIdempotencyKey,
        LocalDateTime occurredAt
    ) {
        return new PaymentTransaction(
            userId,
            subscriptionId,
            subscriptionPeriodId,
            transactionAmount,
            processingReferenceAt,
            periodStartDate,
            periodEndDate,
            externalRequestIdempotencyKey,
            occurredAt
        );
    }

    /** 다음 이용 기간의 정기결제를 외부 요청 전 처리 중 상태로 생성한다. */
    public static PaymentTransaction createRegularPayment(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long transactionAmount,
        LocalDateTime processingReferenceAt,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String externalRequestIdempotencyKey,
        LocalDateTime occurredAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction(
            userId,
            subscriptionId,
            subscriptionPeriodId,
            transactionAmount,
            processingReferenceAt,
            periodStartDate,
            periodEndDate,
            externalRequestIdempotencyKey,
            occurredAt
        );
        transaction.transactionType = PaymentTransactionType.REGULAR_PAYMENT;
        transaction.businessDeduplicationKey = PaymentBusinessKeyGenerator.regularPayment(subscriptionPeriodId);
        return transaction;
    }

    public static PaymentTransaction createCancellation(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long refundId,
        Long originalPaymentTransactionId,
        PaymentTransactionType transactionType,
        Long transactionAmount,
        LocalDateTime processingReferenceAt,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String externalRequestIdempotencyKey,
        LocalDateTime occurredAt
    ) {
        if (transactionType != PaymentTransactionType.CANCELLATION_BEFORE_START
            && transactionType != PaymentTransactionType.NEXT_PERIOD_FULL_CANCELLATION) {
            throw new IllegalArgumentException("Unsupported period cancellation transaction type");
        }
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        transaction.userId = requirePositive(userId, "userId");
        transaction.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        transaction.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        transaction.refundId = requirePositive(refundId, "refundId");
        transaction.originalPaymentTransactionId = requirePositive(originalPaymentTransactionId, "originalPaymentTransactionId");
        transaction.transactionType = requireNonNull(transactionType, "transactionType");
        transaction.transactionAmount = requirePositive(transactionAmount, "transactionAmount");
        transaction.processingReferenceAt = requireNonNull(processingReferenceAt, "processingReferenceAt");
        transaction.periodStartDate = requireNonNull(periodStartDate, "periodStartDate");
        transaction.periodEndDate = requireNonNull(periodEndDate, "periodEndDate");
        transaction.businessDeduplicationKey = PaymentBusinessKeyGenerator.cancellation(refundId, originalPaymentTransactionId);
        transaction.externalRequestIdempotencyKey = requireText(externalRequestIdempotencyKey, "externalRequestIdempotencyKey");
        transaction.status = PaymentTransactionStatus.PROCESSING;
        transaction.paymentStateVersion = 0L;
        transaction.occurredAt = requireNonNull(occurredAt, "occurredAt");
        return transaction;
    }

    public static PaymentTransaction createSettingChangePayment(
        Long userId, Long subscriptionId, Long subscriptionPeriodId, Long subscriptionSettingId,
        Long transactionAmount, LocalDateTime processingReferenceAt, LocalDate periodStartDate,
        LocalDate periodEndDate, LocalDate settingEffectiveDate,
        String externalRequestIdempotencyKey, LocalDateTime occurredAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        transaction.userId = requirePositive(userId, "userId");
        transaction.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        transaction.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        transaction.subscriptionSettingId = requirePositive(subscriptionSettingId, "subscriptionSettingId");
        transaction.transactionType = PaymentTransactionType.SETTING_CHANGE_PAYMENT;
        transaction.transactionAmount = requirePositive(transactionAmount, "transactionAmount");
        transaction.processingReferenceAt = requireNonNull(processingReferenceAt, "processingReferenceAt");
        transaction.periodStartDate = requireNonNull(periodStartDate, "periodStartDate");
        transaction.periodEndDate = requireNonNull(periodEndDate, "periodEndDate");
        if (periodEndDate.isBefore(periodStartDate)) throw new IllegalArgumentException("Invalid payment period");
        transaction.settingEffectiveDate = requireNonNull(settingEffectiveDate, "settingEffectiveDate");
        transaction.businessDeduplicationKey = PaymentBusinessKeyGenerator.settingChange(subscriptionSettingId);
        transaction.externalRequestIdempotencyKey = requireText(externalRequestIdempotencyKey, "externalRequestIdempotencyKey");
        transaction.status = PaymentTransactionStatus.PROCESSING;
        transaction.paymentStateVersion = 0L;
        transaction.occurredAt = requireNonNull(occurredAt, "occurredAt");
        return transaction;
    }

    public static PaymentTransaction createSettingChangeCancellation(
        Long userId, Long subscriptionId, Long subscriptionPeriodId, Long subscriptionSettingId,
        Long refundId, Long originalPaymentTransactionId, Long transactionAmount,
        LocalDateTime processingReferenceAt, LocalDate periodStartDate, LocalDate periodEndDate,
        LocalDate settingEffectiveDate, String externalRequestIdempotencyKey, LocalDateTime occurredAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        transaction.userId = requirePositive(userId, "userId");
        transaction.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        transaction.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        transaction.subscriptionSettingId = requirePositive(subscriptionSettingId, "subscriptionSettingId");
        transaction.refundId = requirePositive(refundId, "refundId");
        transaction.originalPaymentTransactionId = requirePositive(originalPaymentTransactionId, "originalPaymentTransactionId");
        transaction.transactionType = PaymentTransactionType.SETTING_CHANGE_PARTIAL_CANCELLATION;
        transaction.transactionAmount = requirePositive(transactionAmount, "transactionAmount");
        transaction.processingReferenceAt = requireNonNull(processingReferenceAt, "processingReferenceAt");
        transaction.periodStartDate = requireNonNull(periodStartDate, "periodStartDate");
        transaction.periodEndDate = requireNonNull(periodEndDate, "periodEndDate");
        if (periodEndDate.isBefore(periodStartDate)) throw new IllegalArgumentException("Invalid payment period");
        transaction.settingEffectiveDate = requireNonNull(settingEffectiveDate, "settingEffectiveDate");
        transaction.businessDeduplicationKey = PaymentBusinessKeyGenerator.cancellation(refundId, originalPaymentTransactionId);
        transaction.externalRequestIdempotencyKey = requireText(externalRequestIdempotencyKey, "externalRequestIdempotencyKey");
        transaction.status = PaymentTransactionStatus.PROCESSING;
        transaction.paymentStateVersion = 0L;
        transaction.occurredAt = requireNonNull(occurredAt, "occurredAt");
        return transaction;
    }

    /**
     * 첫 구독 결제의 외부 성공 응답을 거래에 반영한다.
     *
     * <p>승인된 거래 금액을 원 결제금액과 취소 가능 금액으로 확정하고,
     * 현재 외부 요청 멱등성 키를 비운 뒤 상태 변경 순번을 증가시킨다.</p>
     *
     * @throws IllegalStateException 거래가 처리 중 상태가 아니거나 현재 외부 요청 키가 없는 경우
     */
    public void markAsSucceeded() {
        requireProcessing();
        this.originalPaymentAmount = transactionAmount;
        this.cumulativeCancelAmount = 0L;
        this.cancelableAmount = transactionAmount;
        complete(PaymentTransactionStatus.SUCCESS);
    }

    /**
     * 첫 구독 결제의 외부 실패 응답을 거래에 반영한다.
     *
     * <p>승인된 원 결제가 아니므로 원 결제금액과 취소 금액 필드는 설정하지 않는다.
     * 현재 외부 요청 멱등성 키를 비우고 상태 변경 순번만 증가시킨다.</p>
     *
     * @throws IllegalStateException 거래가 처리 중 상태가 아니거나 현재 외부 요청 키가 없는 경우
     */
    public void markAsFailed() {
        requireProcessing();
        complete(PaymentTransactionStatus.FAILED);
    }

    public void markCancellationSucceeded() {
        requireCancellationTransaction();
        requireProcessing();
        complete(PaymentTransactionStatus.SUCCESS);
    }

    public void markCancellationFailed() {
        requireCancellationTransaction();
        markAsFailed();
    }

    public void retryCancellation(String newIdempotencyKey) {
        requireCancellationTransaction();
        if (status != PaymentTransactionStatus.FAILED) {
            throw new IllegalStateException("Only a failed cancellation can be retried");
        }
        externalRequestIdempotencyKey = requireText(newIdempotencyKey, "newIdempotencyKey");
        status = PaymentTransactionStatus.PROCESSING;
        paymentStateVersion++;
    }

    public void applySuccessfulCancellation(long amount) {
        if (status != PaymentTransactionStatus.SUCCESS
            || originalPaymentAmount == null || cumulativeCancelAmount == null || cancelableAmount == null
            || amount <= 0 || amount > cancelableAmount) {
            throw new IllegalStateException("Cancellation amount exceeds original payment balance");
        }
        cumulativeCancelAmount = Math.addExact(cumulativeCancelAmount, amount);
        cancelableAmount -= amount;
    }

    /** 고객 해지로 같은 날 13시 정기결제 재시도를 중단한다. */
    public void stopRetry() {
        if (status != PaymentTransactionStatus.RETRY_WAITING) {
            throw new IllegalStateException("Only a retry waiting payment transaction can be stopped");
        }
        status = PaymentTransactionStatus.RETRY_STOPPED;
        paymentStateVersion++;
    }

    /** 오전 정기결제의 명시적 실패를 같은 날 재시도 대기 상태로 확정한다. */
    public void waitForRegularPaymentRetry() {
        requireRegularPaymentTransaction();
        requireProcessing();
        complete(PaymentTransactionStatus.RETRY_WAITING);
    }

    /** 재시도 대기 정기결제를 새 외부 멱등성 키로 다시 처리 중 상태로 변경한다. */
    public void startRegularPaymentRetry(String newIdempotencyKey) {
        requireRegularPaymentTransaction();
        if (status != PaymentTransactionStatus.RETRY_WAITING) {
            throw new IllegalStateException("Only a retry waiting regular payment can be retried");
        }
        externalRequestIdempotencyKey = requireText(newIdempotencyKey, "newIdempotencyKey");
        status = PaymentTransactionStatus.PROCESSING;
        paymentStateVersion++;
    }

    private void complete(PaymentTransactionStatus completedStatus) {
        this.externalRequestIdempotencyKey = null;
        this.status = completedStatus;
        this.paymentStateVersion++;
    }

    private void requireProcessing() {
        if (status != PaymentTransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only a processing payment transaction can be completed");
        }
        if (externalRequestIdempotencyKey == null) {
            throw new IllegalStateException("A processing payment transaction must have an external request idempotency key");
        }
    }

    private void requireCancellationTransaction() {
        if (transactionType != PaymentTransactionType.CANCELLATION_BEFORE_START
            && transactionType != PaymentTransactionType.NEXT_PERIOD_FULL_CANCELLATION
            && transactionType != PaymentTransactionType.SETTING_CHANGE_PARTIAL_CANCELLATION
            && transactionType != PaymentTransactionType.DELIVERY_PARTIAL_CANCELLATION) {
            throw new IllegalStateException("Only an original payment cancellation can use this operation");
        }
    }

    private void requireRegularPaymentTransaction() {
        if (transactionType != PaymentTransactionType.REGULAR_PAYMENT) {
            throw new IllegalStateException("Only a regular payment transaction supports retry");
        }
    }

    private static Long requirePositive(Long value, String fieldName) {
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
