package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.SubscriptionServiceApplication;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.global.kafka.delivery.DeliveryOrderPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryOrderKafkaSchedulerTest {

    @Test
    void 스케줄러_컴포넌트와_스케줄_기능이_애플리케이션에_등록된다() {
        assertThat(DeliveryOrderKafkaScheduler.class.getAnnotation(Component.class)).isNotNull();
        assertThat(SubscriptionServiceApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }

    @Test
    void 최초_배송_주문_발행은_매일_15시_KST로_등록된다() throws NoSuchMethodException {
        Scheduled scheduled = scheduled("publishInitialOrders");

        assertThat(scheduled.cron()).isEqualTo("0 0 15 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 실패_배송_주문_재발행은_매일_16시_KST로_등록된다() throws NoSuchMethodException {
        Scheduled scheduled = scheduled("publishFailedOrdersOnce");

        assertThat(scheduled.cron()).isEqualTo("0 0 16 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 최초_배송_주문_발행은_KST_기준일을_전달한다() {
        DeliveryOrderPublisherService publisherService = mock(DeliveryOrderPublisherService.class);
        KstReferenceTimeProvider timeProvider = mock(KstReferenceTimeProvider.class);
        when(timeProvider.now()).thenReturn(LocalDateTime.of(2026, 9, 5, 15, 0));
        DeliveryOrderKafkaScheduler scheduler = new DeliveryOrderKafkaScheduler(publisherService, timeProvider);

        scheduler.publishInitialOrders();

        verify(publisherService).publishInitialOrders(LocalDate.of(2026, 9, 5));
    }

    @Test
    void 실패_배송_주문_재발행은_KST_기준일을_전달한다() {
        DeliveryOrderPublisherService publisherService = mock(DeliveryOrderPublisherService.class);
        KstReferenceTimeProvider timeProvider = mock(KstReferenceTimeProvider.class);
        when(timeProvider.now()).thenReturn(LocalDateTime.of(2026, 9, 5, 16, 0));
        DeliveryOrderKafkaScheduler scheduler = new DeliveryOrderKafkaScheduler(publisherService, timeProvider);

        scheduler.publishFailedOrdersOnce();

        verify(publisherService).publishFailedOrdersOnce(LocalDate.of(2026, 9, 5));
    }

    private Scheduled scheduled(String methodName) throws NoSuchMethodException {
        Method method = DeliveryOrderKafkaScheduler.class.getMethod(methodName);
        return method.getAnnotation(Scheduled.class);
    }
}
