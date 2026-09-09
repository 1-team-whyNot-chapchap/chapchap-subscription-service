package com.chapchap.subscription.global.kafka.auth;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Auth Projection이 변한 구독만 커밋 이후 Kafka에 통지한다. */
@Service
public class AuthSubscriptionStatusPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuthSubscriptionKafkaProperties properties;

    public AuthSubscriptionStatusPublisher(KafkaTemplate<String, Object> kafkaTemplate, AuthSubscriptionKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishAfterCommit(Subscription subscription, SubscriptionStatus previous, SubscriptionStatus next, LocalDateTime occurredAt) {
        AuthSubscriptionStatus previousProjection = AuthSubscriptionStatus.from(previous);
        AuthSubscriptionStatus nextProjection = AuthSubscriptionStatus.from(next);
        if (previousProjection == nextProjection) return;
        int subscriptionVersion = subscription.increaseAuthSubscriptionVersion();
        SubscriptionStatusChangedEvent event = new SubscriptionStatusChangedEvent(
            stableEventId(subscription.getId(), subscriptionVersion), SubscriptionStatusChangedEvent.EVENT_TYPE, 1,
            occurredAt.atOffset(ZoneOffset.ofHours(9)), subscription.getUserId(),
            new SubscriptionStatusChangedEvent.Data(nextProjection, subscriptionVersion)
        );
        Runnable publish = () -> kafkaTemplate.send(properties.getTopic(), subscription.getUserId().toString(), event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }

    private String stableEventId(Long subscriptionId, int subscriptionVersion) {
        return UUID.nameUUIDFromBytes((SubscriptionStatusChangedEvent.EVENT_TYPE + ":" + subscriptionId + ":" + subscriptionVersion)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
