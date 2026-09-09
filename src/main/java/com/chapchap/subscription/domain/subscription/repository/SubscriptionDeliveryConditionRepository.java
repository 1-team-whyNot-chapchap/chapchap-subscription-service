package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionDeliveryConditionRepository
        extends JpaRepository<SubscriptionDeliveryCondition, Long> {

    // 구독 설정ID로 요일 별 배송 조건 불러오기
    List<SubscriptionDeliveryCondition> findAllBySubscriptionSettingId(
            Long subscriptionSettingId
    );

    @Query("""
            SELECT CASE WHEN COUNT(condition) > 0 THEN true ELSE false END
            FROM SubscriptionDeliveryCondition condition
            JOIN SubscriptionSetting setting ON setting.id = condition.subscriptionSettingId
            WHERE condition.addressId = :addressId
              AND setting.status = :status
              AND setting.effectiveStartDate <= :today
              AND (setting.effectiveEndExclusiveDate IS NULL OR setting.effectiveEndExclusiveDate > :today)
            """)
    boolean existsCurrentConditionByAddressId(
            @Param("addressId") Long addressId,
            @Param("status") SubscriptionSettingStatus status,
            @Param("today") LocalDate today
    );
}
