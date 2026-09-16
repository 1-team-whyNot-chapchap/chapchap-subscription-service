package com.chapchap.subscription.global.scheduler;

import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.global.kafka.delivery.DeliveryOrderPublisherService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryOrderKafkaScheduler {
    private final DeliveryOrderPublisherService publisherService;
    private final KstReferenceTimeProvider timeProvider;

    public DeliveryOrderKafkaScheduler(
        DeliveryOrderPublisherService publisherService,
        KstReferenceTimeProvider timeProvider
    ) {
        this.publisherService = publisherService;
        this.timeProvider = timeProvider;
    }

    @Scheduled(cron = "${DELIVERY_ORDER_INITIAL_CRON}", zone = "Asia/Seoul")
    public void publishInitialOrders() {
        publisherService.publishInitialOrders(timeProvider.now().toLocalDate());
    }

    @Scheduled(cron = "${DELIVERY_ORDER_RETRY_CRON}", zone = "Asia/Seoul")
    public void publishFailedOrdersOnce() {
        publisherService.publishFailedOrdersOnce(timeProvider.now().toLocalDate());
    }
}
