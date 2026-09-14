package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.response.CurrentSubscriptionResponse;
import com.chapchap.subscription.domain.subscription.response.SettingChangeBaselineResponse;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeInProgressException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;

/** 다음 변경의 입력·비교 기준만 읽으며 주문·결제·현재 이용 정보를 변경하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingChangeBaselineQueryService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionDeliveryConditionRepository conditionRepository;
    private final PlanRepository planRepository;
    private final AddressRepository addressRepository;
    private final KstReferenceTimeProvider timeProvider;
    private final SubscriptionScheduleCalculator scheduleCalculator;

    public SettingChangeBaselineResponse getBaseline(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }
        var subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(SubscriptionNotFoundException::new);
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            throw new SubscriptionChangeNotAllowedException();
        }
        var latest = settingRepository.findTopBySubscriptionIdOrderBySettingSequenceDesc(subscription.getId())
            .orElseThrow(() -> inconsistent("설정"));
        if (latest.getStatus() == SubscriptionSettingStatus.CHANGE_PENDING) {
            throw new SubscriptionChangeInProgressException();
        }
        var effectiveDate = scheduleCalculator.calculateReflectionDate(timeProvider.now());
        var applicable = settingRepository.findApplicableSettings(
            subscription.getId(), SubscriptionSettingStatus.ACTIVE, effectiveDate);
        if (applicable.size() != 1) {
            throw inconsistent("적용 예정일의 유효 설정");
        }
        var setting = applicable.getFirst();
        var plan = planRepository.findById(setting.getPlanId())
            .orElseThrow(() -> inconsistent("플랜"));
        var conditions = conditionRepository.findAllBySubscriptionSettingId(setting.getId());
        if (conditions.isEmpty()) {
            throw inconsistent("배송 조건");
        }
        var addressIds = conditions.stream().map(SubscriptionDeliveryCondition::getAddressId).distinct().toList();
        var addresses = new HashMap<Long, Address>();
        addressRepository.findAllById(addressIds).forEach(address -> {
            if (!userId.equals(address.getUserId()) || address.getDeletedAt() != null) {
                throw inconsistent("배송지 소유권 또는 삭제 상태");
            }
            addresses.put(address.getId(), address);
        });
        if (addresses.size() != addressIds.size()) {
            throw inconsistent("배송지 참조");
        }
        var responseConditions = conditions.stream()
            .sorted(Comparator.comparingInt(condition -> condition.getDeliveryWeekday().toDayOfWeek().getValue()))
            .map(condition -> {
                var address = addresses.get(condition.getAddressId());
                return new CurrentSubscriptionResponse.DeliveryConditionResponse(
                    condition.getDeliveryWeekday(), condition.getMealQuantity(), condition.getDeliveryTimeSlot(),
                    new CurrentSubscriptionResponse.AddressResponse(
                        address.getPublicId(), address.getName(), address.getRecipientName(), address.getRecipientPhone(),
                        address.getPostalCode(), address.getAddressLine1(), address.getAddressLine2()));
            }).toList();
        return new SettingChangeBaselineResponse(subscription.getPublicId(), effectiveDate,
            new CurrentSubscriptionResponse.PlanResponse(plan.getPublicId(), plan.getName(), plan.getDescription(), plan.getUnitPrice()),
            responseConditions);
    }

    private IllegalStateException inconsistent(String target) {
        return new IllegalStateException("설정 변경 기준의 " + target + " 데이터 조합이 올바르지 않습니다.");
    }
}
