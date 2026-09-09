package com.chapchap.subscription.global.kafka.delivery;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Delivery가 소비하는 V1 주문 전달 Event다. */
public record SubscriptionDeliveryOrderReadyEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "SUBSCRIPTION_DELIVERY_ORDER_READY";

    public record Data(
        String orderId,
        LocalDate deliveryDate,
        String deliverySlot,
        int lunchboxQuantity,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethod,
        String deliveryMethodDetail,
        String entranceInformation,
        boolean termsAgreed,
        OffsetDateTime termsAgreedAt,
        List<MenuItem> menuItems
    ) {}

    public record MenuItem(String menuId, String menuName, int quantity) {}
}
