package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Subscription> findWithLockById(Long id);

    /** 설정 변경처럼 같은 고객의 구독 상태와 설정 순번을 함께 결정하는 작업용 잠금 조회다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Subscription> findWithLockByUserId(Long userId);
}
