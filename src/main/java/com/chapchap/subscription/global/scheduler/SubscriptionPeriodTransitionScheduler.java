package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.subscription.service.SubscriptionPeriodTransitionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 00:00 KST 직후 시작·전환 작업이 누락돼도 같은 날 처리할 수 있도록 매분 확인한다. */
@Component
public class SubscriptionPeriodTransitionScheduler {
    private final SubscriptionPeriodTransitionService transitionService;
    private final KstReferenceTimeProvider timeProvider;

    public SubscriptionPeriodTransitionScheduler(
        SubscriptionPeriodTransitionService transitionService,
        KstReferenceTimeProvider timeProvider
    ) {
        this.transitionService = transitionService;
        this.timeProvider = timeProvider;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void transitionScheduledPeriods() {
        transitionService.transitionScheduledPeriods(timeProvider.now().toLocalDate());
    }
}
