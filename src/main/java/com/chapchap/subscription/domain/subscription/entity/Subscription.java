package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscriptions_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_subscriptions_user_id", columnNames = "user_id")
        },
        check = {
                @CheckConstraint(
                        name = "chk_subscriptions_auth_subscription_version",
                        constraint = "auth_subscription_version >= 0"
                ),
                @CheckConstraint(
                        name = "chk_subscriptions_status",
                        constraint = "status IN ('AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS', "
                                + "'CANCELLATION_SCHEDULED', 'PAYMENT_FAILED', "
                                + "'CANCELED_BEFORE_START', 'ENDED')"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(name = "auth_subscription_version", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 0")
    private Integer authSubscriptionVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "is_first_subscription_discount_used", nullable = false, options = "DEFAULT 0")
    private boolean isFirstSubscriptionDiscountUsed;

    @Column(name = "cancellation_requested_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime cancellationRequestedAt;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT UNSIGNED DEFAULT 0")
    private Long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    // 새 구독 데이터 생성
    public static Subscription create(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }

        Subscription subscription = new Subscription();
        subscription.publicId = UUID.randomUUID().toString();
        subscription.userId = userId;
        subscription.authSubscriptionVersion = 0;
        subscription.status = SubscriptionStatus.AWAITING_CONFIRMATION;
        subscription.isFirstSubscriptionDiscountUsed = false;
        return subscription;
    }

    // AWAITING_CONFIRMATION(확정 대기) -> SCHEDULED(시작 예정) 상태 변경
    public SubscriptionStatus markScheduled() {
        requireStatus(SubscriptionStatus.AWAITING_CONFIRMATION);
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.SCHEDULED;
        return previousStatus;
    }

    // AWAITING_CONFIRMATION(확정 대기) -> PAYMENT_FAILED(결제 실패) 상태 변경
    public SubscriptionStatus markPaymentFailed() {
        requireStatus(SubscriptionStatus.AWAITING_CONFIRMATION);
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.PAYMENT_FAILED;
        return previousStatus;
    }

    // SCHEDULED(시작 예정) -> IN_PROGRESS(이용 중) 상태 변경
    public SubscriptionStatus startFirstPeriod() {
        requireStatus(SubscriptionStatus.SCHEDULED);
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.IN_PROGRESS;
        return previousStatus;
    }

    /** 현재 이용 기간은 유지한 채 다음 자동 갱신만 중단하도록 해지 예정으로 전환한다. */
    public SubscriptionStatus scheduleCancellation(LocalDateTime requestedAt) {
        requireStatus(SubscriptionStatus.IN_PROGRESS);
        if (requestedAt == null) throw new IllegalArgumentException("해지 요청 시각이 필요합니다.");
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.CANCELLATION_SCHEDULED;
        cancellationRequestedAt = requestedAt;
        return previousStatus;
    }

    public SubscriptionStatus cancelBeforeStart(LocalDateTime canceledAt) {
        requireStatus(SubscriptionStatus.SCHEDULED);
        if (canceledAt == null) throw new IllegalArgumentException("시작 취소 시각이 필요합니다.");
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.CANCELED_BEFORE_START;
        cancellationRequestedAt = canceledAt;
        return previousStatus;
    }

    /** 해지 예정 또는 다음 기간 정기결제 최종 실패 뒤 실제 구독을 종료한다. */
    public SubscriptionStatus end() {
        if (status != SubscriptionStatus.CANCELLATION_SCHEDULED && status != SubscriptionStatus.IN_PROGRESS) {
            throw new IllegalStateException("해지 예정 또는 이용 중 구독만 종료할 수 있습니다.");
        }
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.ENDED;
        return previousStatus;
    }

    // 특정 조건(결제 실패, 시작 전 취소, 종료)을 가진 구독 건을 재신청할 수 있도록 상태를 '승인 대기'로 초기화
    public SubscriptionStatus prepareReapplication() {
        if (status != SubscriptionStatus.PAYMENT_FAILED
                && status != SubscriptionStatus.CANCELED_BEFORE_START
                && status != SubscriptionStatus.ENDED) {
            throw new IllegalStateException("결제 실패·시작 취소·종료 구독만 재신청할 수 있습니다.");
        }
        SubscriptionStatus previousStatus = status;
        status = SubscriptionStatus.AWAITING_CONFIRMATION;
        cancellationRequestedAt = null;
        return previousStatus;
    }

    // 첫 구독 할인이 적용되면 DB에 기록
    public void markFirstSubscriptionDiscountUsed() {
        if (status != SubscriptionStatus.SCHEDULED) {
            throw new IllegalStateException("첫 결제 성공으로 시작 예정이 된 구독만 할인 사용 처리할 수 있습니다.");
        }
        isFirstSubscriptionDiscountUsed = true;
    }

    /** Auth Projection 상태가 실제로 바뀔 때만 고객별 전달 순번을 증가시킨다. */
    public int increaseAuthSubscriptionVersion() {
        authSubscriptionVersion = Math.addExact(authSubscriptionVersion, 1);
        return authSubscriptionVersion;
    }

    // 현재 구독 상태가 특정 작업에 필요한 상태와 일치하는지 확인
    private void requireStatus(SubscriptionStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("구독 상태가 " + requiredStatus + " 상태여야 합니다.");
        }
    }
}
