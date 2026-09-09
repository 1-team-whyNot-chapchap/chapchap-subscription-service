package com.chapchap.subscription.domain.subscription.response;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import java.time.LocalDate;

public record SettingChangeResponse(
    SubscriptionSettingStatus settingStatus,
    DifferenceType differenceType,
    long differenceAmount,
    LocalDate effectiveStartDate,
    boolean paymentConfirmationRequired,
    CurrentPaymentMethod currentPaymentMethod,
    RefundResult refund
) {
    public enum DifferenceType { INCREASE, DECREASE, NO_PRICE_CHANGE }
    public record CurrentPaymentMethod(String paymentMethodId, String cardCompany, String maskedCardNumber) {}
    public record RefundResult(String refundId, RefundStatus status, long requestedAmount,
        long refundedAmount, long unprocessedAmount) {}
}
