package com.chapchap.subscription.global.kafka.customer;

import java.time.OffsetDateTime;

/** Customer 배송지 Read Model과 알림에 전달하는 최소 변경 사실이다. */
public record DeliveryAddressChangedEvent(String eventId, String eventType, int version, OffsetDateTime occurredAt, Long userId, Data data) {
    public static final String EVENT_TYPE = "DELIVERY_ADDRESS_CHANGED";
    public record Data(String deliveryAddressId, long deliveryAddressVersion, String deliveryAddressLabel) {}
}
