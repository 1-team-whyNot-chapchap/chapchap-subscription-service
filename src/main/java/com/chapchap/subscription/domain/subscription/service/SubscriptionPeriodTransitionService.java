package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 시작일이 된 첫 이용 기간을 시작하거나 연속된 이용 기간을 전환한다. */
@Service
public class SubscriptionPeriodTransitionService {
    private static final int FIRST_PERIOD_SEQUENCE = 1;
    private static final String ACTOR = "SYSTEM";
    private static final String FIRST_PERIOD_STARTED_REASON = "FIRST_PERIOD_STARTED";

    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final KstReferenceTimeProvider timeProvider;

    public SubscriptionPeriodTransitionService(
        SubscriptionPeriodRepository periodRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionStatusHistoryRepository historyRepository,
        KstReferenceTimeProvider timeProvider
    ) {
        this.periodRepository = periodRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.historyRepository = historyRepository;
        this.timeProvider = timeProvider;
    }

    /** KST 오늘 시작하는 예정 이용 기간을 현재 상태와 순번에 맞게 처리한다. */
    @Transactional
    public void transitionScheduledPeriods(LocalDate today) {
        List<PeriodCandidate> candidates = periodRepository.findAllByStatusAndPeriodStartDate(
            SubscriptionPeriodStatus.SCHEDULED, today
        ).stream().map(PeriodCandidate::from).toList();

        LocalDateTime changedAt = timeProvider.now();
        for (PeriodCandidate candidate : candidates) {
            transitionIfStillScheduled(candidate, today, changedAt);
        }
    }

    private void transitionIfStillScheduled(PeriodCandidate candidate, LocalDate today, LocalDateTime changedAt) {
        Subscription subscription = subscriptionRepository.findWithLockById(candidate.subscriptionId()).orElse(null);
        if (subscription == null) {
            return;
        }

        SubscriptionPeriod next = periodRepository.findWithLockById(candidate.periodId()).orElse(null);
        if (!isScheduledForToday(next, candidate.subscriptionId(), today)) {
            return;
        }

        if (next.getPeriodSequence() == FIRST_PERIOD_SEQUENCE) {
            startFirstPeriod(subscription, next, changedAt);
            return;
        }

        transitionToNextPeriod(subscription, next);
    }

    private void startFirstPeriod(
        Subscription subscription,
        SubscriptionPeriod firstPeriod,
        LocalDateTime changedAt
    ) {
        if (subscription.getStatus() != SubscriptionStatus.SCHEDULED) {
            return;
        }

        SubscriptionStatus previousStatus = subscription.startFirstPeriod();
        firstPeriod.start();
        historyRepository.save(SubscriptionStatusHistory.create(
            subscription.getId(), previousStatus, SubscriptionStatus.IN_PROGRESS,
            ACTOR, FIRST_PERIOD_STARTED_REASON, changedAt
        ));
    }

    private void transitionToNextPeriod(Subscription subscription, SubscriptionPeriod next) {
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            return;
        }

        int previousSequence = next.getPeriodSequence() - 1;
        SubscriptionPeriod previous = periodRepository
            .findWithLockBySubscriptionIdAndPeriodSequence(subscription.getId(), previousSequence)
            .orElse(null);
        if (!isConsecutiveInProgressPeriod(previous, next)) {
            return;
        }

        previous.end();
        next.start();
    }

    private boolean isScheduledForToday(SubscriptionPeriod period, Long subscriptionId, LocalDate today) {
        return period != null
            && period.getSubscriptionId().equals(subscriptionId)
            && period.getStatus() == SubscriptionPeriodStatus.SCHEDULED
            && period.getPeriodStartDate().equals(today);
    }

    private boolean isConsecutiveInProgressPeriod(SubscriptionPeriod previous, SubscriptionPeriod next) {
        return previous != null
            && previous.getStatus() == SubscriptionPeriodStatus.IN_PROGRESS
            && previous.getPeriodSequence() + 1 == next.getPeriodSequence()
            && previous.getPeriodEndDate().plusDays(1).equals(next.getPeriodStartDate());
    }

    private record PeriodCandidate(Long periodId, Long subscriptionId) {
        private static PeriodCandidate from(SubscriptionPeriod period) {
            return new PeriodCandidate(period.getId(), period.getSubscriptionId());
        }
    }
}
