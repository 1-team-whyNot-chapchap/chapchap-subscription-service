package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "subscription_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_periods_subscription_sequence",
                columnNames = {"subscription_id", "period_sequence"}
        ),
        indexes = {
                @Index(
                        name = "idx_subscription_periods_status_start",
                        columnList = "status, period_start_date"
                ),
                @Index(
                        name = "idx_subscription_periods_status_end",
                        columnList = "status, period_end_date"
                )
        },
        check = {
                @CheckConstraint(name = "chk_subscription_periods_sequence", constraint = "period_sequence >= 1"),
                @CheckConstraint(
                        name = "chk_subscription_periods_status",
                        constraint = "status IN ('AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS', "
                                + "'ENDED', 'CANCELED_BEFORE_START', 'PAYMENT_FAILED')"
                ),
                @CheckConstraint(
                        name = "chk_subscription_periods_dates",
                        constraint = "period_start_date <= period_end_date"
                ),
                @CheckConstraint(
                        name = "chk_subscription_periods_start_cancellation",
                        constraint = "(status = 'CANCELED_BEFORE_START' "
                                + "AND start_canceled_at IS NOT NULL AND start_cancel_reason IS NOT NULL) OR "
                                + "(status <> 'CANCELED_BEFORE_START' "
                                + "AND start_canceled_at IS NULL AND start_cancel_reason IS NULL)"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Column(name = "period_sequence", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer periodSequence;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionPeriodStatus status;

    @Column(name = "calculation_reference_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime calculationReferenceAt;

    @Column(name = "start_canceled_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime startCanceledAt;

    @Column(name = "start_cancel_reason", length = 40)
    private String startCancelReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    // 새 구독 기간 생성
    public static SubscriptionPeriod createAwaitingConfirmation(
            Long subscriptionId,
            int periodSequence,
            LocalDate periodStartDate,
            LocalDateTime calculationReferenceAt
    ) {
        if (subscriptionId == null || subscriptionId <= 0 || periodSequence < 1) {
            throw new IllegalArgumentException("구독 식별자와 이용 기간 순번을 확인해 주세요.");
        }
        if (periodStartDate == null || calculationReferenceAt == null) {
            throw new IllegalArgumentException("이용 기간 계산 기준값이 누락되었습니다.");
        }

        SubscriptionPeriod period = new SubscriptionPeriod();
        period.subscriptionId = subscriptionId;
        period.periodSequence = periodSequence;
        period.periodStartDate = periodStartDate;
        period.periodEndDate = periodStartDate.plusDays(27);
        period.status = SubscriptionPeriodStatus.AWAITING_CONFIRMATION;
        period.calculationReferenceAt = calculationReferenceAt;
        return period;
    }

    // 이용 기간 상태를 SCHEDULED(시작 예정)으로 설정
    public void markScheduled() {
        requireAwaitingConfirmation();
        status = SubscriptionPeriodStatus.SCHEDULED;
    }

    // 이용 기간 상태를 PAYMENT_FAILED(결제 실패)으로 설정
    public void markPaymentFailed() {
        requireAwaitingConfirmation();
        status = SubscriptionPeriodStatus.PAYMENT_FAILED;
    }

    // SCHEDULED(시작 예정) -> IN_PROGRESS(진행 중) 상태 변경
    public void start() {
        if (status != SubscriptionPeriodStatus.SCHEDULED) {
            throw new IllegalStateException("시작 예정 이용 기간만 이용을 시작할 수 있습니다.");
        }
        status = SubscriptionPeriodStatus.IN_PROGRESS;
    }

    // IN_PROGRESS(진행 중) -> ENDED(종료) 상태 변경
    public void end() {
        if (status != SubscriptionPeriodStatus.IN_PROGRESS) {
            throw new IllegalStateException("이용 중인 기간만 종료할 수 있습니다.");
        }
        status = SubscriptionPeriodStatus.ENDED;
    }

    public void cancelBeforeStart(LocalDateTime canceledAt, String reason) {
        if (status != SubscriptionPeriodStatus.SCHEDULED || canceledAt == null || reason == null || reason.isBlank()) {
            throw new IllegalStateException("시작 예정 기간만 시작 취소할 수 있습니다.");
        }
        status = SubscriptionPeriodStatus.CANCELED_BEFORE_START;
        startCanceledAt = canceledAt;
        startCancelReason = reason;
    }

    /** 오전 정기결제 실패 후 아직 확정되지 않은 다음 기간의 재시도를 취소한다. */
    public void cancelAwaitingRegularPayment(LocalDateTime canceledAt, String reason) {
        if (status != SubscriptionPeriodStatus.AWAITING_CONFIRMATION
                || canceledAt == null || reason == null || reason.isBlank()) {
            throw new IllegalStateException("확정 대기 기간만 정기결제 재시도를 취소할 수 있습니다.");
        }
        status = SubscriptionPeriodStatus.CANCELED_BEFORE_START;
        startCanceledAt = canceledAt;
        startCancelReason = reason;
    }

    private void requireAwaitingConfirmation() {
        if (status != SubscriptionPeriodStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("확정 대기 이용 기간만 결제 결과를 반영할 수 있습니다.");
        }
    }
}
