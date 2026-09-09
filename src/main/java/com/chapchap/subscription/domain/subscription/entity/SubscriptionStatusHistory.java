package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "subscription_status_histories",
        indexes = @Index(
                name = "idx_subscription_status_histories_subscription_changed_id",
                columnList = "subscription_id, changed_at, id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private SubscriptionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 30)
    private SubscriptionStatus nextStatus;

    @Column(name = "change_actor", nullable = false, length = 30)
    private String changeActor;

    @Column(name = "change_reason", nullable = false, length = 100)
    private String changeReason;

    @Column(name = "changed_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime changedAt;

    // 구독 상태 이력(구독 상태 변경 기록) 데이터 생성
    public static SubscriptionStatusHistory create(
            Long subscriptionId,
            SubscriptionStatus previousStatus,
            SubscriptionStatus nextStatus,
            String changeActor,
            String changeReason,
            LocalDateTime changedAt
    ) {
        if (subscriptionId == null || subscriptionId <= 0) {
            throw new IllegalArgumentException("구독 식별자는 양수여야 합니다.");
        }
        if (nextStatus == null || isBlank(changeActor) || isBlank(changeReason) || changedAt == null) {
            throw new IllegalArgumentException("상태 이력의 필수값이 누락되었습니다.");
        }
        if (previousStatus == nextStatus) {
            throw new IllegalArgumentException("변경 전후 구독 상태는 같을 수 없습니다.");
        }

        SubscriptionStatusHistory history = new SubscriptionStatusHistory();
        history.subscriptionId = subscriptionId;
        history.previousStatus = previousStatus;
        history.nextStatus = nextStatus;
        history.changeActor = changeActor;
        history.changeReason = changeReason;
        history.changedAt = changedAt;
        return history;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
