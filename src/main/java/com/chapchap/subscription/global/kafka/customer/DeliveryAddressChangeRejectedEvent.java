package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

public record DeliveryAddressChangeRejectedEvent(String eventId, String eventType, int version, OffsetDateTime occurredAt, Long userId, Data data) {
    public static final String EVENT_TYPE = "DELIVERY_ADDRESS_CHANGE_REJECTED";
    public record Data(String deliveryAddressId, String rejectionCode) {}
}
