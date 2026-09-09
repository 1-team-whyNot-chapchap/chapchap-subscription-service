package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.subscription.service.SubscriptionTerminationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 00:00 KST 이후 전날 종료 대상을 실제 종료로 확정한다. */
@Component
public class SubscriptionTerminationScheduler {
    private final SubscriptionTerminationService terminationService;
    private final KstReferenceTimeProvider timeProvider;
    public SubscriptionTerminationScheduler(SubscriptionTerminationService terminationService, KstReferenceTimeProvider timeProvider) { this.terminationService = terminationService; this.timeProvider = timeProvider; }
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void terminateDueSubscriptions() { terminationService.terminateDueSubscriptions(timeProvider.now().toLocalDate()); }
}
