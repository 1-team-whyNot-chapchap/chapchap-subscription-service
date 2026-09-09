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
        name = "subscription_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_settings_subscription_sequence",
                columnNames = {"subscription_id", "setting_sequence"}
        ),
        indexes = @Index(
                name = "idx_subscription_settings_subscription_status_start",
                columnList = "subscription_id, status, effective_start_date"
        ),
        check = {
                @CheckConstraint(name = "chk_subscription_settings_sequence", constraint = "setting_sequence >= 1"),
                @CheckConstraint(
                        name = "chk_subscription_settings_status",
                        constraint = "status IN ('AWAITING_CONFIRMATION', 'CHANGE_PENDING', 'ACTIVE', "
                                + "'PAYMENT_FAILED', 'CHANGE_NOT_APPLIED', 'ENDED')"
                ),
                @CheckConstraint(
                        name = "chk_subscription_settings_reference",
                        constraint = "(setting_sequence = 1 AND processing_reference_at IS NULL) OR "
                                + "(setting_sequence > 1 AND processing_reference_at IS NOT NULL)"
                ),
                @CheckConstraint(
                        name = "chk_subscription_settings_dates",
                        constraint = "effective_end_exclusive_date IS NULL OR "
                                + "effective_end_exclusive_date >= effective_start_date"
                ),
                @CheckConstraint(
                        name = "chk_subscription_settings_confirmation",
                        constraint = "(status IN ('ACTIVE', 'CHANGE_NOT_APPLIED') AND confirmed_at IS NOT NULL) OR "
                                + "(status NOT IN ('ACTIVE', 'CHANGE_NOT_APPLIED') AND confirmed_at IS NULL)"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long planId;

    @Column(name = "setting_sequence", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer settingSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionSettingStatus status;

    @Column(name = "processing_reference_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime processingReferenceAt;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_exclusive_date")
    private LocalDate effectiveEndExclusiveDate;

    @Column(name = "confirmed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;


    public static SubscriptionSetting createFirstAwaitingConfirmation(
            Long subscriptionId,
            Long planId,
            LocalDate effectiveStartDate
    ) {
        return createAwaitingConfirmation(subscriptionId, planId, 1, null, effectiveStartDate);
    }

    // 구독 설정 버전 데이터 수정
    public static SubscriptionSetting createAwaitingConfirmation(
            Long subscriptionId,
            Long planId,
            int settingSequence,
            LocalDateTime processingReferenceAt,
            LocalDate effectiveStartDate
    ) {
        if (subscriptionId == null || subscriptionId <= 0 || planId == null || planId <= 0) {
            throw new IllegalArgumentException("구독과 플랜 식별자는 양수여야 합니다.");
        }
        if (settingSequence < 1 || effectiveStartDate == null) {
            throw new IllegalArgumentException("설정 순번과 적용 시작일을 확인해 주세요.");
        }
        if ((settingSequence == 1 && processingReferenceAt != null)
                || (settingSequence > 1 && processingReferenceAt == null)) {
            throw new IllegalArgumentException("첫 설정과 후속 설정의 처리 기준 시각 조건이 올바르지 않습니다.");
        }

        SubscriptionSetting setting = new SubscriptionSetting();
        setting.subscriptionId = subscriptionId;
        setting.planId = planId;
        setting.settingSequence = settingSequence;
        setting.status = SubscriptionSettingStatus.AWAITING_CONFIRMATION;
        setting.processingReferenceAt = processingReferenceAt;
        setting.effectiveStartDate = effectiveStartDate;
        return setting;
    }

    /**
     * 설정 변경 요청에서 결제·환불 결과를 기다리는 다음 설정 버전을 만든다.
     * 기존 유효 설정은 이 상태만으로 종료하지 않으며, 변경 확정 때만 종료일을 기록한다.
     */
    public static SubscriptionSetting createChangePending(
            Long subscriptionId,
            Long planId,
            int settingSequence,
            LocalDateTime processingReferenceAt,
            LocalDate effectiveStartDate
    ) {
        SubscriptionSetting setting = createAwaitingConfirmation(
                subscriptionId, planId, settingSequence, processingReferenceAt, effectiveStartDate
        );
        if (settingSequence == 1) {
            throw new IllegalArgumentException("설정 변경은 두 번째 설정 버전부터 만들 수 있습니다.");
        }
        setting.status = SubscriptionSettingStatus.CHANGE_PENDING;
        return setting;
    }

    // 결제 성공 후 상태를 ACTIVE(유효)로 변경
    public void activate(LocalDateTime confirmedAt) {
        if (status != SubscriptionSettingStatus.AWAITING_CONFIRMATION || confirmedAt == null) {
            throw new IllegalStateException("확정 대기 설정과 확정 시각이 있어야 활성화할 수 있습니다.");
        }
        status = SubscriptionSettingStatus.ACTIVE;
        this.confirmedAt = confirmedAt;
    }

    /** 결제·환불 또는 차액 없음이 확정된 설정 변경을 유효 상태로 바꾼다. */
    public void activateChange(LocalDateTime confirmedAt) {
        if (status != SubscriptionSettingStatus.CHANGE_PENDING || confirmedAt == null) {
            throw new IllegalStateException("변경 대기 설정과 확정 시각이 있어야 활성화할 수 있습니다.");
        }
        status = SubscriptionSettingStatus.ACTIVE;
        this.confirmedAt = confirmedAt;
    }

    /**
     * 확정된 새 설정이 적용될 때 이전 유효 설정의 적용 범위를 닫는다.
     * 같은 적용일에 다시 변경된 설정은 실제 적용된 날짜가 없으므로 빈 적용 구간으로 보존한다.
     */
    public void closeAt(LocalDate effectiveEndExclusiveDate) {
        if (status != SubscriptionSettingStatus.ACTIVE || effectiveEndExclusiveDate == null
                || effectiveEndExclusiveDate.isBefore(effectiveStartDate)) {
            throw new IllegalStateException("유효 설정의 종료일은 시작일보다 앞설 수 없습니다.");
        }
        this.effectiveEndExclusiveDate = effectiveEndExclusiveDate;
    }

    /** 결제 또는 최초 원 결제 취소 실패로 변경이 적용되지 않았음을 기록한다. */
    public void markChangeNotApplied(LocalDateTime confirmedAt) {
        if (status != SubscriptionSettingStatus.CHANGE_PENDING || confirmedAt == null) {
            throw new IllegalStateException("변경 대기 설정만 변경 미적용으로 확정할 수 있습니다.");
        }
        status = SubscriptionSettingStatus.CHANGE_NOT_APPLIED;
        this.confirmedAt = confirmedAt;
    }

    // 결제 실패 시 상태를 PAYMENT_FAILED(결제 실패)로 변경
    public void markPaymentFailed() {
        if (status != SubscriptionSettingStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("확정 대기 설정만 결제 실패로 변경할 수 있습니다.");
        }
        status = SubscriptionSettingStatus.PAYMENT_FAILED;
    }
}
