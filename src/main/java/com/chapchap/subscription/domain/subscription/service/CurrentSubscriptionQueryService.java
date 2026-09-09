package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.response.CurrentSubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 현재 구독 조회를 데이터 변경과 외부 호출 없이 수행한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentSubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository subscriptionPeriodRepository;
    private final SubscriptionSettingRepository subscriptionSettingRepository;
    private final SubscriptionDeliveryConditionRepository deliveryConditionRepository;
    private final PlanRepository planRepository;
    private final AddressRepository addressRepository;
    private final KstReferenceTimeProvider referenceTimeProvider;

    public CurrentSubscriptionResponse getCurrentSubscription(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }

        Subscription subscription = subscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null) {
            return null;
        }

        if (hasNoCurrentConfiguration(subscription.getStatus())) {
            return statusOnly(subscription);
        }

        QueryTarget target = resolveQueryTarget(subscription);
        return configured(subscription, target.period(), target.setting(), userId);
    }

    private boolean hasNoCurrentConfiguration(SubscriptionStatus status) {
        return status == SubscriptionStatus.PAYMENT_FAILED
                || status == SubscriptionStatus.CANCELED_BEFORE_START
                || status == SubscriptionStatus.ENDED;
    }

    private CurrentSubscriptionResponse statusOnly(Subscription subscription) {
        return new CurrentSubscriptionResponse(
                subscription.getPublicId(),
                subscription.getStatus(),
                null,
                null,
                subscription.getCancellationRequestedAt(),
                null,
                List.of()
        );
    }

    private QueryTarget resolveQueryTarget(Subscription subscription) {
        Long subscriptionId = requirePositiveId(subscription.getId(), "구독");

        return switch (subscription.getStatus()) {
            case AWAITING_CONFIRMATION -> {
                SubscriptionPeriod period = requirePeriod(
                        subscriptionPeriodRepository
                                .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                                        subscriptionId,
                                        SubscriptionPeriodStatus.AWAITING_CONFIRMATION
                                )
                );
                SubscriptionSetting setting = requireSetting(
                        subscriptionSettingRepository
                                .findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
                                        subscriptionId,
                                        SubscriptionSettingStatus.AWAITING_CONFIRMATION
                                )
                );
                yield new QueryTarget(period, setting);
            }
            case SCHEDULED -> {
                SubscriptionPeriod period = requirePeriod(
                        subscriptionPeriodRepository
                                .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                                        subscriptionId,
                                        SubscriptionPeriodStatus.SCHEDULED
                                )
                );
                yield new QueryTarget(
                        period,
                        requireApplicableSetting(subscriptionId, period.getPeriodStartDate())
                );
            }
            case IN_PROGRESS, CANCELLATION_SCHEDULED -> {
                LocalDate todayKst = referenceTimeProvider.now().toLocalDate();
                SubscriptionPeriod period = requirePeriod(
                        subscriptionPeriodRepository
                                .findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
                                        subscriptionId,
                                        SubscriptionPeriodStatus.IN_PROGRESS,
                                        todayKst,
                                        todayKst
                                )
                );
                yield new QueryTarget(
                        period,
                        requireApplicableSetting(subscriptionId, todayKst)
                );
            }
            default -> throw inconsistentData("지원하지 않는 구독 상태");
        };
    }

    private SubscriptionSetting requireApplicableSetting(Long subscriptionId, LocalDate targetDate) {
        List<SubscriptionSetting> settings = subscriptionSettingRepository.findApplicableSettings(
                subscriptionId,
                SubscriptionSettingStatus.ACTIVE,
                targetDate
        );
        if (settings.size() != 1) {
            throw inconsistentData("현재 적용 설정");
        }
        return settings.getFirst();
    }

    private CurrentSubscriptionResponse configured(
            Subscription subscription,
            SubscriptionPeriod period,
            SubscriptionSetting setting,
            Long userId
    ) {
        Plan plan = planRepository.findById(requirePositiveId(setting.getPlanId(), "플랜 참조"))
                .orElseThrow(() -> inconsistentData("플랜"));

        List<SubscriptionDeliveryCondition> conditions = deliveryConditionRepository
                .findAllBySubscriptionSettingId(requirePositiveId(setting.getId(), "설정"));
        if (conditions.isEmpty()) {
            throw inconsistentData("배송 조건");
        }

        Map<Long, Address> addresses = findAddresses(conditions, userId);
        List<CurrentSubscriptionResponse.DeliveryConditionResponse> conditionResponses = conditions.stream()
                .sorted(Comparator.comparingInt(
                        condition -> condition.getDeliveryWeekday().toDayOfWeek().getValue()
                ))
                .map(condition -> toResponse(condition, addresses.get(condition.getAddressId())))
                .toList();

        return new CurrentSubscriptionResponse(
                subscription.getPublicId(),
                subscription.getStatus(),
                period.getPeriodStartDate(),
                period.getPeriodEndDate(),
                subscription.getCancellationRequestedAt(),
                new CurrentSubscriptionResponse.PlanResponse(
                        plan.getPublicId(),
                        plan.getName(),
                        plan.getDescription(),
                        plan.getUnitPrice()
                ),
                conditionResponses
        );
    }

    private Map<Long, Address> findAddresses(
            List<SubscriptionDeliveryCondition> conditions,
            Long userId
    ) {
        List<Long> addressIds = conditions.stream()
                .map(SubscriptionDeliveryCondition::getAddressId)
                .distinct()
                .toList();

        Map<Long, Address> addresses = new HashMap<>();
        addressRepository.findAllById(addressIds).forEach(address -> {
            if (!userId.equals(address.getUserId()) || address.getDeletedAt() != null) {
                throw inconsistentData("배송지 소유권 또는 삭제 상태");
            }
            addresses.put(address.getId(), address);
        });

        if (addresses.size() != addressIds.size()) {
            throw inconsistentData("배송지");
        }
        return addresses;
    }

    private CurrentSubscriptionResponse.DeliveryConditionResponse toResponse(
            SubscriptionDeliveryCondition condition,
            Address address
    ) {
        if (address == null) {
            throw inconsistentData("배송지 참조");
        }

        return new CurrentSubscriptionResponse.DeliveryConditionResponse(
                condition.getDeliveryWeekday(),
                condition.getMealQuantity(),
                condition.getDeliveryTimeSlot(),
                new CurrentSubscriptionResponse.AddressResponse(
                        address.getPublicId(),
                        address.getName(),
                        address.getRecipientName(),
                        address.getRecipientPhone(),
                        address.getPostalCode(),
                        address.getAddressLine1(),
                        address.getAddressLine2()
                )
        );
    }

    private SubscriptionPeriod requirePeriod(java.util.Optional<SubscriptionPeriod> period) {
        return period.orElseThrow(() -> inconsistentData("현재 이용 기간"));
    }

    private SubscriptionSetting requireSetting(java.util.Optional<SubscriptionSetting> setting) {
        return setting.orElseThrow(() -> inconsistentData("현재 구독 설정"));
    }

    private Long requirePositiveId(Long id, String target) {
        if (id == null || id <= 0) {
            throw inconsistentData(target);
        }
        return id;
    }

    private IllegalStateException inconsistentData(String target) {
        return new IllegalStateException(target + " 데이터 조합이 올바르지 않습니다.");
    }

    private record QueryTarget(
            SubscriptionPeriod period,
            SubscriptionSetting setting
    ) {
    }
}
