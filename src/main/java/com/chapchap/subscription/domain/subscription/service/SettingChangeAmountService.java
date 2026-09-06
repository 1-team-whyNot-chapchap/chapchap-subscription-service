package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SettingChangeAmountService {
    private final SubscriptionSettingRepository settings;
    private final OrderRepository orders;
    private final PaymentAllocationRepository allocations;

    public SettingChangeAmountService(SubscriptionSettingRepository settings, OrderRepository orders,
        PaymentAllocationRepository allocations) {
        this.settings = settings;
        this.orders = orders;
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public SettingChangeAmountSnapshot analyze(Long settingId) {
        var setting = settings.findById(settingId).orElseThrow();
        if (setting.getStatus() != SubscriptionSettingStatus.CHANGE_PENDING) {
            throw new IllegalStateException("Only a pending setting change can be analyzed");
        }
        List<Order> oldOrders = orders.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(
            setting.getSubscriptionId(), OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT,
            setting.getEffectiveStartDate());
        List<Order> newOrders = orders.findAllBySubscriptionSettingId(settingId);
        List<Long> oldOrderIds = oldOrders.stream().map(Order::getId).toList();
        List<PaymentAllocation> oldAllocations = oldOrderIds.isEmpty()
            ? List.of() : allocations.findAllByOrderIdInOrderByIdAsc(oldOrderIds);
        long oldAmount = oldAllocations.stream().mapToLong(PaymentAllocation::currentCancelableAmount).sum();
        long newAmount = newOrders.stream().mapToLong(Order::getActualAllocatedAmount).sum();
        return new SettingChangeAmountSnapshot(setting, oldOrders, newOrders, oldAllocations, oldAmount, newAmount);
    }
}
