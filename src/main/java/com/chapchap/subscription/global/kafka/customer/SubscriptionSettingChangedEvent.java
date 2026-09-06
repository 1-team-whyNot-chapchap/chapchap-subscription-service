package com.chapchap.subscription.global.kafka.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SubscriptionSettingChangedEvent(
    String eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    Long userId,
    Data data
) {
    public static final String EVENT_TYPE = "SUBSCRIPTION_SETTING_CHANGED";

    public record Data(
        int settingVersion,
        LocalDate effectiveDate,
        String planDisplayName,
        List<DeliveryCondition> deliveryConditions
    ) {
    }

    public record DeliveryCondition(
        String weekday,
        int mealQuantity,
        String deliveryAddressLabel,
        String deliveryTimeSlot
    ) {
    }
}
