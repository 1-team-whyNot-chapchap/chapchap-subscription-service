package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 설정 변경 요청의 검증·주문 계획·변경 대기 데이터 저장을 연결하는 내부 흐름이다. */
@Service
public class SettingChangePreparationFlowService {
    private final SettingChangePreparationService preparationService;
    private final SettingChangeOrderPlanService orderPlanService;
    private final SettingChangePendingSaveService pendingSaveService;
    private final SubscriptionRepository subscriptionRepository;

    public SettingChangePreparationFlowService(
        SettingChangePreparationService preparationService,
        SettingChangeOrderPlanService orderPlanService,
        SettingChangePendingSaveService pendingSaveService,
        SubscriptionRepository subscriptionRepository
    ) {
        this.preparationService = preparationService;
        this.orderPlanService = orderPlanService;
        this.pendingSaveService = pendingSaveService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * 결제·환불을 시작하기 전에 변경 대기 설정·배송조건·주문을 함께 저장한다.
     * 이 메서드는 아직 외부 HTTP API에 노출하지 않는다.
     */
    @Transactional
    public PreparedSettingChange prepare(Long userId, SettingChangePreparationRequest request) {
        // 이후 조회·저장까지 같은 트랜잭션에서 잠금을 유지해 동시 변경의 설정 순번 충돌을 막는다.
        subscriptionRepository.findWithLockByUserId(userId);
        SettingChangePreparationResult prepared = preparationService.prepare(userId, request);
        SettingChangeOrderPreparationCommand orderPlan = orderPlanService.plan(prepared);
        SettingChangePendingSaveResult saved = pendingSaveService.save(prepared, orderPlan);
        return new PreparedSettingChange(
            prepared.subscriptionId(),
            saved.settingId(),
            saved.settingSequence(),
            prepared.referenceAt(),
            prepared.effectiveStartDate(),
            saved.orderIds().size()
        );
    }
}
