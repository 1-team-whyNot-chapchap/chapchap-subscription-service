package com.chapchap.subscription.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.GeneratedColumn;

import java.time.LocalDateTime;
import java.util.UUID;

/** 환불 대상과 여러 원 결제 취소의 집계 결과를 보존하는 환불 업무다. */
@Getter
@Entity
@Table(
    name = "refunds",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refunds_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_refunds_business_key", columnNames = "business_deduplication_key"),
        @UniqueConstraint(name = "uk_refunds_period", columnNames = "subscription_period_id"),
        @UniqueConstraint(name = "uk_refunds_setting", columnNames = "subscription_setting_id"),
        @UniqueConstraint(name = "uk_refunds_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_refunds_external_delivery", columnNames = "external_delivery_id")
    },
    indexes = {
        @Index(name = "idx_refunds_subscription_requested", columnList = "subscription_id, requested_at"),
        @Index(name = "idx_refunds_status_requested", columnList = "status, requested_at")
    }
)
@Check(name = "ck_refunds_amount", constraints = "refund_amount >= 1")
@Check(
    name = "ck_refunds_successful_amount",
    constraints = "successful_refund_amount >= 0 AND successful_refund_amount <= refund_amount"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {
    private static final String PUBLIC_ID_PREFIX = "REF-";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 40, columnDefinition = "CHAR(40)")
    private String publicId;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private RefundType refundType;

    @Column(name = "subscription_period_id", columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionPeriodId;

    @Column(name = "subscription_setting_id", columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionSettingId;

    @Column(name = "order_id", columnDefinition = "BIGINT UNSIGNED")
    private Long orderId;

    @Column(name = "external_delivery_id", length = 36, columnDefinition = "CHAR(36)")
    private String externalDeliveryId;

    @Column(name = "refund_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long refundAmount;

    @Column(name = "successful_refund_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long successfulRefundAmount;

    @GeneratedColumn("refund_amount - successful_refund_amount")
    @Column(name = "unprocessed_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long unprocessedAmount;

    @Column(name = "business_deduplication_key", nullable = false, length = 255)
    private String businessDeduplicationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private RefundStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false,
        columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
        columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
        columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    public static Refund createPeriodCancellation(Long subscriptionId, Long subscriptionPeriodId,
        RefundType refundType, Long refundAmount) {
        if (refundType != RefundType.CANCELLATION_BEFORE_START
            && refundType != RefundType.NEXT_PERIOD_FULL_CANCELLATION) {
            throw new IllegalArgumentException("Period cancellation requires a period cancellation refund type");
        }
        Refund refund = new Refund();
        refund.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        refund.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        refund.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        refund.refundType = refundType;
        refund.refundAmount = requirePositive(refundAmount, "refundAmount");
        refund.successfulRefundAmount = 0L;
        refund.businessDeduplicationKey = (refundType == RefundType.CANCELLATION_BEFORE_START
            ? "REFUND:START:" : "REFUND:NEXT:") + subscriptionPeriodId;
        refund.status = RefundStatus.PENDING;
        return refund;
    }

    public static Refund createSettingChangeReduction(
        Long subscriptionId, Long subscriptionSettingId, Long refundAmount
    ) {
        Refund refund = new Refund();
        refund.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        refund.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        refund.subscriptionSettingId = requirePositive(subscriptionSettingId, "subscriptionSettingId");
        refund.refundType = RefundType.SETTING_CHANGE_REDUCTION;
        refund.refundAmount = requirePositive(refundAmount, "refundAmount");
        refund.successfulRefundAmount = 0L;
        refund.businessDeduplicationKey = "REFUND:CHANGE:" + subscriptionSettingId;
        refund.status = RefundStatus.PENDING;
        return refund;
    }

    /** Delivery가 확정한 배송 건 한 건의 저장된 주문 배분금액을 환불 대상으로 만든다. */
    public static Refund createDeliveryPartialCancellation(
        Long subscriptionId, Long orderId, String externalDeliveryId, Long refundAmount
    ) {
        Refund refund = new Refund();
        refund.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        refund.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        refund.orderId = requirePositive(orderId, "orderId");
        refund.externalDeliveryId = requireText(externalDeliveryId, "externalDeliveryId");
        refund.refundType = RefundType.DELIVERY_PARTIAL_CANCELLATION;
        refund.refundAmount = requirePositive(refundAmount, "refundAmount");
        refund.successfulRefundAmount = 0L;
        refund.businessDeduplicationKey = "REFUND:DELIVERY:" + externalDeliveryId;
        refund.status = RefundStatus.PENDING;
        return refund;
    }

    public void addSuccessfulAmount(long amount, LocalDateTime completedAt) {
        requirePending();
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        successfulRefundAmount = Math.addExact(successfulRefundAmount, amount);
        if (successfulRefundAmount > refundAmount) {
            throw new IllegalStateException("Successful refund amount exceeds requested amount");
        }
        if (successfulRefundAmount.equals(refundAmount)) {
            status = RefundStatus.COMPLETED;
            this.completedAt = requireNonNull(completedAt, "completedAt");
        }
    }

    public void markFailed(String failureReason) {
        requirePending();
        this.failureReason = requireText(failureReason, "failureReason");
        status = successfulRefundAmount == 0L ? RefundStatus.FAILED : RefundStatus.REVIEW_REQUIRED;
    }

    public void retry() {
        if (status != RefundStatus.FAILED || successfulRefundAmount != 0L) {
            throw new IllegalStateException("Only a fully failed period refund can be retried");
        }
        status = RefundStatus.PENDING;
        failureReason = null;
    }

    private void requirePending() {
        if (status != RefundStatus.PENDING) throw new IllegalStateException("Only a pending refund can be completed");
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) throw new IllegalArgumentException(fieldName + " must be positive");
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) throw new IllegalArgumentException(fieldName + " must not be null");
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return value;
    }
}
