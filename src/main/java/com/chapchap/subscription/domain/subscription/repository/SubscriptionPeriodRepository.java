package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPeriodRepository extends JpaRepository<SubscriptionPeriod, Long> {

    // 특정 구독에 속한 기간 중 가장 최근 구독 기간 순번을 불러옴
    Optional<SubscriptionPeriod> findTopBySubscriptionIdOrderByPeriodSequenceDesc(Long subscriptionId);

    Optional<SubscriptionPeriod> findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
            Long subscriptionId,
            SubscriptionPeriodStatus status
    );

    Optional<SubscriptionPeriod> findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
            Long subscriptionId,
            SubscriptionPeriodStatus status,
            LocalDate startDate,
            LocalDate endDate
    );

    List<SubscriptionPeriod> findAllByStatusAndPeriodStartDate(
        SubscriptionPeriodStatus status,
        LocalDate periodStartDate
    );

    List<SubscriptionPeriod> findAllByStatusAndPeriodEndDate(
        SubscriptionPeriodStatus status,
        LocalDate periodEndDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionPeriod> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionPeriod> findWithLockBySubscriptionIdAndPeriodSequence(
        Long subscriptionId,
        Integer periodSequence
    );
}
