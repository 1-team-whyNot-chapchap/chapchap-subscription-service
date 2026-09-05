package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.global.exception.subscription.PlanNotFoundException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeInProgressException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

/** SUB-FN-006의 비결제 사전 검증 전용 서비스다. DB 상태·결제·환불을 변경하지 않는다. */
@Service
public class SettingChangePreparationService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final PlanRepository planRepository;
    private final AddressService addressService;
    private final KstReferenceTimeProvider timeProvider;
    private final OrderRepository orderRepository;

    public SettingChangePreparationService(
        SubscriptionRepository subscriptionRepository,
        SubscriptionSettingRepository settingRepository,
        PlanRepository planRepository,
        AddressService addressService,
        KstReferenceTimeProvider timeProvider,
        OrderRepository orderRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.settingRepository = settingRepository;
        this.planRepository = planRepository;
        this.addressService = addressService;
        this.timeProvider = timeProvider;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public SettingChangePreparationResult prepare(Long userId, SettingChangePreparationRequest request) {
        validateRequest(request);
        Subscription subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(SubscriptionNotFoundException::new);
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            throw new SubscriptionChangeNotAllowedException();
        }
        SubscriptionSetting latest = settingRepository.findTopBySubscriptionIdOrderBySettingSequenceDesc(subscription.getId())
            .orElseThrow(() -> new IllegalStateException("현재 설정이 없습니다."));
        if (latest.getStatus() == SubscriptionSettingStatus.CHANGE_PENDING) {
            throw new SubscriptionChangeInProgressException();
        }
        Plan plan = planRepository.findByPublicId(request.planId())
            .orElseThrow(PlanNotFoundException::new);
        HashSet<Object> weekdays = new HashSet<>();
        List<SettingChangeDraft.DeliveryCondition> draftConditions = new ArrayList<>();
        for (SettingChangePreparationRequest.DeliveryCondition condition : request.deliveryConditions()) {
            if (condition.weekday() == null || condition.deliveryTimeSlot() == null
                || condition.mealQuantity() == null || condition.mealQuantity() < 1
                || condition.mealQuantity() > 6 || !weekdays.add(condition.weekday())) {
                throw new IllegalArgumentException("배송 조건이 올바르지 않습니다.");
            }
            Address address = addressService.requireActiveAddress(userId, condition.addressId());
            draftConditions.add(new SettingChangeDraft.DeliveryCondition(
                condition.weekday(),
                condition.mealQuantity(),
                address.getId(),
                condition.deliveryTimeSlot()
            ));
        }
        LocalDateTime referenceAt = timeProvider.now();
        LocalDate effectiveStartDate = effectiveStartDate(referenceAt);
        SettingChangeDraft draft = new SettingChangeDraft(
            latest.getSettingSequence() + 1,
            plan.getId(),
            effectiveStartDate,
            draftConditions
        );
        List<Long> replaceableOrderIds = orderRepository
            .findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(
                subscription.getId(),
                OrderStatus.ACTIVE,
                OrderKafkaDeliveryStatus.NOT_SENT,
                effectiveStartDate
            )
            .stream()
            .map(order -> order.getId())
            .toList();
        return new SettingChangePreparationResult(
            userId, subscription.getId(), latest.getId(), plan.getId(), referenceAt, effectiveStartDate, draft, replaceableOrderIds
        );
    }

    private void validateRequest(SettingChangePreparationRequest request) {
        if (request == null || request.deliveryConditions() == null
            || request.deliveryConditions().isEmpty() || request.deliveryConditions().size() > 6) {
            throw new IllegalArgumentException("배송 조건은 1~6개여야 합니다.");
        }
    }

    private LocalDate effectiveStartDate(LocalDateTime referenceAt) {
        LocalDate date = referenceAt.toLocalDate();
        LocalDate result = referenceAt.toLocalTime().isBefore(LocalTime.of(14, 0))
            ? date.plusDays(1)
            : date.plusDays(2);
        return result.getDayOfWeek() == DayOfWeek.SUNDAY ? result.plusDays(1) : result;
    }
}
