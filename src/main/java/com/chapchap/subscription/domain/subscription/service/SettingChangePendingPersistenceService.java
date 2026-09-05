package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 검증된 설정 변경 초안을 변경 대기 설정과 배송조건으로 저장한다.
 * 변경 주문 생성·결제·환불을 직접 수행하지 않으며, 최종 요청 흐름에서는 주문 생성과 같은 트랜잭션으로 호출해야 한다.
 */
@Service
public class SettingChangePendingPersistenceService {
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionDeliveryConditionRepository conditionRepository;

    public SettingChangePendingPersistenceService(
        SubscriptionSettingRepository settingRepository,
        SubscriptionDeliveryConditionRepository conditionRepository
    ) {
        this.settingRepository = settingRepository;
        this.conditionRepository = conditionRepository;
    }

    @Transactional
    public SettingChangePendingPersistenceResult persist(SettingChangePreparationResult prepared) {
        if (prepared == null || prepared.draft() == null) {
            throw new IllegalArgumentException("저장할 설정 변경 초안이 없습니다.");
        }

        SettingChangeDraft draft = prepared.draft();
        SubscriptionSetting savedSetting = settingRepository.save(SubscriptionSetting.createChangePending(
            prepared.subscriptionId(),
            draft.planId(),
            draft.settingSequence(),
            prepared.referenceAt(),
            draft.effectiveStartDate()
        ));

        if (savedSetting.getId() == null) {
            throw new IllegalStateException("저장된 설정 식별자가 없습니다.");
        }

        List<SubscriptionDeliveryCondition> conditions = draft.deliveryConditions().stream()
            .map(condition -> SubscriptionDeliveryCondition.create(
                savedSetting.getId(),
                condition.weekday(),
                condition.mealQuantity(),
                condition.addressId(),
                condition.deliveryTimeSlot()
            ))
            .toList();
        conditionRepository.saveAll(conditions);

        return new SettingChangePendingPersistenceResult(savedSetting.getId(), savedSetting.getSettingSequence());
    }
}
