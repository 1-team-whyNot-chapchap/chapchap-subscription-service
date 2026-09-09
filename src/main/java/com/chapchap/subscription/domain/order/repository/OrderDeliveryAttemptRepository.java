package com.chapchap.subscription.domain.order.repository;

import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttempt;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttemptResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface OrderDeliveryAttemptRepository extends JpaRepository<OrderDeliveryAttempt, Long> {
    boolean existsByOrderIdAndAttemptSequenceAndResult(Long orderId, int attemptSequence, OrderDeliveryAttemptResult result);

    boolean existsByOrderIdAndAttemptSequenceAndResultAndAttemptedAtBetween(
        Long orderId, int attemptSequence, OrderDeliveryAttemptResult result,
        LocalDateTime startInclusive, LocalDateTime endExclusive
    );
}
