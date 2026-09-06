package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 현재 이용 기간 종료 뒤 실제 종료 조건을 만족한 구독을 종료한다. */
@Service
public class SubscriptionTerminationService {
    private static final String ACTOR = "SYSTEM";
    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final AuthSubscriptionStatusPublisher authPublisher;
    private final KstReferenceTimeProvider timeProvider;
    private final CustomerSubscriptionNotificationPublisher customerNotificationPublisher;

    public SubscriptionTerminationService(SubscriptionPeriodRepository periodRepository, SubscriptionRepository subscriptionRepository, SubscriptionStatusHistoryRepository historyRepository, AuthSubscriptionStatusPublisher authPublisher, KstReferenceTimeProvider timeProvider, CustomerSubscriptionNotificationPublisher customerNotificationPublisher) {
        this.periodRepository = periodRepository; this.subscriptionRepository = subscriptionRepository;
        this.historyRepository = historyRepository; this.authPublisher = authPublisher; this.timeProvider = timeProvider;
        this.customerNotificationPublisher = customerNotificationPublisher;
    }

    @Transactional
    public void terminateDueSubscriptions(LocalDate today) {
        LocalDate endedYesterday = today.minusDays(1);
        for (SubscriptionPeriod candidate : periodRepository.findAllByStatusAndPeriodEndDate(SubscriptionPeriodStatus.IN_PROGRESS, endedYesterday)) {
            terminateIfDue(candidate.getId(), endedYesterday, timeProvider.now());
        }
    }

    private void terminateIfDue(Long currentPeriodId, LocalDate endedYesterday, LocalDateTime endedAt) {
        SubscriptionPeriod current = periodRepository.findWithLockById(currentPeriodId).orElse(null);
        if (current == null || current.getStatus() != SubscriptionPeriodStatus.IN_PROGRESS || !current.getPeriodEndDate().equals(endedYesterday)) return;
        Subscription subscription = subscriptionRepository.findWithLockById(current.getSubscriptionId()).orElse(null);
        if (subscription == null || !isTerminationDue(subscription, current)) return;
        SubscriptionStatus previous = subscription.end();
        historyRepository.save(SubscriptionStatusHistory.create(subscription.getId(), previous, SubscriptionStatus.ENDED, ACTOR,
            previous == SubscriptionStatus.CANCELLATION_SCHEDULED ? "CANCELLATION_PERIOD_ENDED" : "REGULAR_PAYMENT_FINAL_FAILURE", endedAt));
        authPublisher.publishAfterCommit(subscription, previous, SubscriptionStatus.ENDED, endedAt);
        customerNotificationPublisher.publishEndedAfterCommit(
            subscription,
            previous == SubscriptionStatus.CANCELLATION_SCHEDULED
                ? "CUSTOMER_CANCELLATION"
                : "REGULAR_PAYMENT_FINAL_FAILURE",
            endedAt
        );
    }

    private boolean isTerminationDue(Subscription subscription, SubscriptionPeriod current) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELLATION_SCHEDULED) return true;
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) return false;
        return periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(subscription.getId())
            .filter(next -> next.getPeriodSequence() == current.getPeriodSequence() + 1)
            .map(next -> next.getStatus() == SubscriptionPeriodStatus.PAYMENT_FAILED).orElse(false);
    }
}
