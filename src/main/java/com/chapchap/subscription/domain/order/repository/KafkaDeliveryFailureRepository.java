package com.chapchap.subscription.domain.order.repository;

import com.chapchap.subscription.domain.order.entity.KafkaDeliveryFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaDeliveryFailureRepository extends JpaRepository<KafkaDeliveryFailure, Long> {
}
