package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubscriptionOngoingStatusReader implements OngoingSubscriptionReader {

    private static final Set<SubscriptionStatus> ONGOING_STATUSES = EnumSet.of(
        SubscriptionStatus.AWAITING_CONFIRMATION,
        SubscriptionStatus.SCHEDULED,
        SubscriptionStatus.IN_PROGRESS,
        SubscriptionStatus.CANCELLATION_SCHEDULED
    );

    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsOngoingSubscription(Long userId) {
        return subscriptionRepository.findByUserId(userId)
            .map(subscription -> ONGOING_STATUSES.contains(subscription.getStatus()))
            .orElse(false);
    }
}
