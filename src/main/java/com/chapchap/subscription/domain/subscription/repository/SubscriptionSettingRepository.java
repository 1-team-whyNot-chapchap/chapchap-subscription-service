package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionSettingRepository extends JpaRepository<SubscriptionSetting, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionSetting> findWithLockById(Long id);

    // 특정 구독의 구독 기간 중 순번이 가장 큰(마지만 회차) 찾기
    Optional<SubscriptionSetting> findTopBySubscriptionIdOrderBySettingSequenceDesc(Long subscriptionId);

    Optional<SubscriptionSetting> findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
            Long subscriptionId,
            SubscriptionSettingStatus status
    );

    @Query("""
            SELECT setting
            FROM SubscriptionSetting setting
            WHERE setting.subscriptionId = :subscriptionId
              AND setting.status = :status
              AND setting.effectiveStartDate <= :targetDate
              AND (
                    setting.effectiveEndExclusiveDate IS NULL
                    OR setting.effectiveEndExclusiveDate > :targetDate
              )
            ORDER BY setting.settingSequence DESC
            """)
    List<SubscriptionSetting> findApplicableSettings(
            @Param("subscriptionId") Long subscriptionId,
            @Param("status") SubscriptionSettingStatus status,
            @Param("targetDate") LocalDate targetDate
    );
}
