package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import java.util.List;

public record SettingChangeAmountSnapshot(
    SubscriptionSetting setting, List<Order> oldOrders, List<Order> newOrders,
    List<PaymentAllocation> oldAllocations, long oldAmount, long newAmount
) {
    public long differenceAmount() { return Math.abs(Math.subtractExact(newAmount, oldAmount)); }
}
