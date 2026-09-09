package com.chapchap.subscription.global.kafka.delivery;

import java.time.OffsetDateTime;

/** Delivery가 발행하는 배송 건 환불 확정 V1 Event다. */
public record DeliveryRefundConfirmedEvent(
    String eventId,
    String eventType,
    Integer version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "DELIVERY_REFUND_CONFIRMED";

    public record Data(
        String deliveryId,
        String orderId,
        OffsetDateTime confirmedAt,
        String reasonCode
    ) {
    }
}
