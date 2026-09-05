package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 변경 대기 설정·배송조건과 변경 대기 주문을 원자적으로 저장하는 내부 조정 서비스다. */
@Service
public class SettingChangePendingSaveService {
    private final SettingChangePendingPersistenceService settingPersistenceService;
    private final SettingChangeOrderService orderService;

    public SettingChangePendingSaveService(
        SettingChangePendingPersistenceService settingPersistenceService,
        SettingChangeOrderService orderService
    ) {
        this.settingPersistenceService = settingPersistenceService;
        this.orderService = orderService;
    }

    @Transactional
    public SettingChangePendingSaveResult save(
        SettingChangePreparationResult prepared,
        SettingChangeOrderPreparationCommand orderPlan
    ) {
        validateConsistency(prepared, orderPlan);
        SettingChangePendingPersistenceResult setting = settingPersistenceService.persist(prepared);
        List<Order> orders = orderService.prepare(orderPlan.withSubscriptionSettingId(setting.settingId()));
        return new SettingChangePendingSaveResult(
            setting.settingId(),
            setting.settingSequence(),
            orders.stream().map(Order::getId).toList()
        );
    }

    private void validateConsistency(
        SettingChangePreparationResult prepared,
        SettingChangeOrderPreparationCommand orderPlan
    ) {
        if (prepared == null || prepared.draft() == null || orderPlan == null) {
            throw new IllegalArgumentException("설정 변경 저장 입력이 누락되었습니다.");
        }
        if (!prepared.subscriptionId().equals(orderPlan.subscriptionId())
            || !prepared.draft().planId().equals(orderPlan.plan().planId())) {
            throw new IllegalArgumentException("설정 변경 초안과 주문 계획의 구독 또는 플랜이 일치하지 않습니다.");
        }
    }
}
