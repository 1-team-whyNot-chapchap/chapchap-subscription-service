package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 결제·환불이 확정한 결과를 설정·주문 상태에 반영한다. 외부 결제 호출은 하지 않는다. */
@Service
public class SettingChangeCompletionService {
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;

    public SettingChangeCompletionService(SubscriptionSettingRepository settingRepository, SubscriptionRepository subscriptionRepository, OrderRepository orderRepository) {
        this.settingRepository = settingRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void complete(Long settingId, SettingChangeCompletionStatus status, LocalDateTime completedAt) {
        if (settingId == null || settingId <= 0 || status == null || completedAt == null) throw new IllegalArgumentException("설정 변경 완료 입력이 올바르지 않습니다.");
        SubscriptionSetting setting = settingRepository.findWithLockById(settingId).orElseThrow(() -> new IllegalStateException("변경 대기 설정을 찾을 수 없습니다."));
        if (setting.getStatus() != SubscriptionSettingStatus.CHANGE_PENDING) throw new IllegalStateException("변경 대기 설정만 완료 처리할 수 있습니다.");
        subscriptionRepository.findWithLockById(setting.getSubscriptionId());
        List<Order> newOrders = orderRepository.findAllBySubscriptionSettingId(settingId);
        if (status == SettingChangeCompletionStatus.NOT_APPLIED) {
            setting.markChangeNotApplied(completedAt);
            newOrders.forEach(Order::markChangeNotApplied);
            return;
        }
        List<Order> existingOrders = orderRepository.findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(setting.getSubscriptionId(), OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, setting.getEffectiveStartDate());
        existingOrders.forEach(Order::inactivateForSettingChange);
        settingRepository.findApplicableSettings(setting.getSubscriptionId(), SubscriptionSettingStatus.ACTIVE, setting.getEffectiveStartDate()).forEach(previous -> previous.closeAt(setting.getEffectiveStartDate()));
        setting.activateChange(completedAt);
        newOrders.forEach(Order::activateChange);
    }
}
