package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.address.entity.Address;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class CustomerDeliveryAddressPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CustomerDeliveryAddressKafkaProperties properties;
    public CustomerDeliveryAddressPublisher(KafkaTemplate<String, Object> kafkaTemplate, CustomerDeliveryAddressKafkaProperties properties) { this.kafkaTemplate = kafkaTemplate; this.properties = properties; }
    public void publishChangedAfterCommit(Address address, LocalDateTime occurredAt) {
        String publicId = address.getPublicId(); long version = address.getDeliveryAddressVersion();
        DeliveryAddressChangedEvent event = new DeliveryAddressChangedEvent(UUID.nameUUIDFromBytes((DeliveryAddressChangedEvent.EVENT_TYPE + ":" + publicId + ":" + version).getBytes(StandardCharsets.UTF_8)).toString(), DeliveryAddressChangedEvent.EVENT_TYPE, 1, occurredAt.atOffset(ZoneOffset.ofHours(9)), address.getUserId(), new DeliveryAddressChangedEvent.Data(publicId, version, address.getName()));
        Runnable send = () -> kafkaTemplate.send(properties.getTopic(), publicId, event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { send.run(); } }); else send.run();
    }
    public void publishRejectedAfterCommit(Address address, String rejectionCode, LocalDateTime occurredAt) {
        String publicId = address.getPublicId();
        DeliveryAddressChangeRejectedEvent event = new DeliveryAddressChangeRejectedEvent(UUID.nameUUIDFromBytes((DeliveryAddressChangeRejectedEvent.EVENT_TYPE + ":" + publicId + ":" + rejectionCode).getBytes(StandardCharsets.UTF_8)).toString(), DeliveryAddressChangeRejectedEvent.EVENT_TYPE, 1, occurredAt.atOffset(ZoneOffset.ofHours(9)), address.getUserId(), new DeliveryAddressChangeRejectedEvent.Data(publicId, rejectionCode));
        Runnable send = () -> kafkaTemplate.send(properties.getTopic(), publicId, event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { send.run(); } }); else send.run();
    }
}
