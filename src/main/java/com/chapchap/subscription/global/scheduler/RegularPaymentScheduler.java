package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.payment.service.RegularPaymentService;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 현재 기간 종료일 09시 정기결제와 같은 날 13시 단일 재시도를 실행한다. */
@Component
public class RegularPaymentScheduler {
    private final RegularPaymentService regularPaymentService;
    private final KstReferenceTimeProvider timeProvider;

    public RegularPaymentScheduler(
        RegularPaymentService regularPaymentService,
        KstReferenceTimeProvider timeProvider
    ) {
        this.regularPaymentService = regularPaymentService;
        this.timeProvider = timeProvider;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void executeInitialPayments() {
        regularPaymentService.executeInitialPayments(timeProvider.now());
    }

    @Scheduled(cron = "0 0 13 * * *", zone = "Asia/Seoul")
    public void executeRetryPayments() {
        regularPaymentService.executeRetryPayments(timeProvider.now());
    }
}
