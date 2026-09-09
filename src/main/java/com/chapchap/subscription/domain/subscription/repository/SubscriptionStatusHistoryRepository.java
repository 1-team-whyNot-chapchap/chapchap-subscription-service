package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionStatusHistoryRepository extends JpaRepository<SubscriptionStatusHistory, Long> {
}
