package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.payment.service.RegularPaymentService;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class RegularPaymentSchedulerTest {
    @Test
    void 오전과_오후_스케줄러는_각각_정기결제_서비스에_위임한다() {
        RegularPaymentService service = mock(RegularPaymentService.class);
        KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
        LocalDateTime referenceAt = LocalDateTime.of(2026, 9, 30, 9, 0);
        when(time.now()).thenReturn(referenceAt);
        RegularPaymentScheduler scheduler = new RegularPaymentScheduler(service, time);

        scheduler.executeInitialPayments();
        scheduler.executeRetryPayments();

        verify(service).executeInitialPayments(referenceAt);
        verify(service).executeRetryPayments(referenceAt);
    }

    @Test
    void 정기결제_스케줄은_한국시간_09시와_13시로_고정한다() throws Exception {
        Scheduled initial = RegularPaymentScheduler.class.getMethod("executeInitialPayments")
            .getAnnotation(Scheduled.class);
        Scheduled retry = RegularPaymentScheduler.class.getMethod("executeRetryPayments")
            .getAnnotation(Scheduled.class);

        assertThat(initial.cron()).isEqualTo("0 0 9 * * *");
        assertThat(initial.zone()).isEqualTo("Asia/Seoul");
        assertThat(retry.cron()).isEqualTo("0 0 13 * * *");
        assertThat(retry.zone()).isEqualTo("Asia/Seoul");
    }
}
