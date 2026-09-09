package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.subscription.service.SubscriptionPeriodTransitionService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionPeriodTransitionSchedulerTest {
    @Test
    void 전환_스케줄러는_KST_매분으로_등록된다() throws NoSuchMethodException {
        assertThat(SubscriptionPeriodTransitionScheduler.class.getAnnotation(Component.class)).isNotNull();
        Method method = SubscriptionPeriodTransitionScheduler.class.getMethod("transitionScheduledPeriods");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 전환_스케줄러는_KST_오늘을_서비스에_전달한다() {
        SubscriptionPeriodTransitionService service = mock(SubscriptionPeriodTransitionService.class);
        KstReferenceTimeProvider timeProvider = mock(KstReferenceTimeProvider.class);
        when(timeProvider.now()).thenReturn(LocalDateTime.of(2026, 9, 7, 0, 1));
        SubscriptionPeriodTransitionScheduler scheduler = new SubscriptionPeriodTransitionScheduler(service, timeProvider);

        scheduler.transitionScheduledPeriods();

        verify(service).transitionScheduledPeriods(LocalDate.of(2026, 9, 7));
    }
}
