package com.chapchap.subscription.global.kafka.customer;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

final class CustomerKafkaPublicationSupport {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    private CustomerKafkaPublicationSupport() {
    }

    static String deterministicEventId(String eventType, String factKey) {
        return UUID.nameUUIDFromBytes((eventType + ":" + factKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static OffsetDateTime toKst(LocalDateTime value) {
        return value.atOffset(KST_OFFSET);
    }

    static void afterCommit(Runnable publication) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publication.run();
                }
            });
            return;
        }
        publication.run();
    }
}
