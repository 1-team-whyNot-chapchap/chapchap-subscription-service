package com.chapchap.subscription.domain.subscription.service;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionSchedule(
        LocalDate reflectionDate,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        List<LocalDate> deliveryDates
) {
    public SubscriptionSchedule {
        deliveryDates = List.copyOf(deliveryDates);
    }
}
