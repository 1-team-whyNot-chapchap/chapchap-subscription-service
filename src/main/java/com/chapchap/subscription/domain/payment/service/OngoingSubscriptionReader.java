package com.chapchap.subscription.domain.payment.service;

public interface OngoingSubscriptionReader {

    boolean existsOngoingSubscription(Long userId);
}
