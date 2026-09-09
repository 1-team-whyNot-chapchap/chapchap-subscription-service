package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.exception.address.AddressNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 현재 이용 기간의 적용일 이후 변경 대기 주문 계획을 자동으로 계산한다. */
@Service
public class SettingChangeOrderPlanService {
    private final PlanRepository planRepository;
    private final MenuRepository menuRepository;
    private final AddressRepository addressRepository;
    private final HolidayRepository holidayRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionDeliveryConditionRepository conditionRepository;
    private final TermsService termsService;

    public SettingChangeOrderPlanService(
        PlanRepository planRepository,
        MenuRepository menuRepository,
        AddressRepository addressRepository,
        HolidayRepository holidayRepository,
        OrderRepository orderRepository,
        SubscriptionPeriodRepository periodRepository,
        SubscriptionSettingRepository settingRepository,
        SubscriptionDeliveryConditionRepository conditionRepository,
        TermsService termsService
    ) {
        this.planRepository = planRepository;
        this.menuRepository = menuRepository;
        this.addressRepository = addressRepository;
        this.holidayRepository = holidayRepository;
        this.orderRepository = orderRepository;
        this.periodRepository = periodRepository;
        this.settingRepository = settingRepository;
        this.conditionRepository = conditionRepository;
        this.termsService = termsService;
    }

    @Transactional(readOnly = true)
    public SettingChangeOrderPreparationCommand plan(SettingChangePreparationResult prepared) {
        if (prepared == null || prepared.draft() == null) {
            throw new IllegalArgumentException("설정 변경 초안이 필요합니다.");
        }
        SettingChangeDraft draft = prepared.draft();
        Plan plan = planRepository.findById(draft.planId())
            .orElseThrow(() -> new IllegalStateException("변경 플랜을 찾을 수 없습니다."));
        UserTermsAgreement agreement = termsService.requireCurrentAgreement(prepared.userId());
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> conditions = conditionsByWeekday(draft);
        List<Order> replacements = orderRepository
            .findAllBySubscriptionIdAndStatusAndKafkaDeliveryStatusAndDeliveryDateGreaterThanEqual(
                prepared.subscriptionId(), OrderStatus.ACTIVE, OrderKafkaDeliveryStatus.NOT_SENT, draft.effectiveStartDate()
            );
        Map<LocalDate, Order> replacementByDate = replacements.stream()
            .collect(java.util.stream.Collectors.toMap(Order::getDeliveryDate, order -> order));
        SubscriptionPeriod currentPeriod = periodRepository
            .findTopBySubscriptionIdAndStatusAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByPeriodSequenceDesc(
                prepared.subscriptionId(), SubscriptionPeriodStatus.IN_PROGRESS,
                draft.effectiveStartDate(), draft.effectiveStartDate()
            )
            .orElse(null);

        SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy = pricingPolicy(
            prepared, plan, conditions, currentPeriod
        );

        List<SettingChangeOrderPreparationCommand.Delivery> deliveries = currentPeriod == null
            ? List.of()
            : deliveries(
                prepared.userId(), prepared.subscriptionId(), plan, currentPeriod, draft, conditions,
                replacementByDate, pricingPolicy
            );
        return new SettingChangeOrderPreparationCommand(
            prepared.userId(), prepared.subscriptionId(), null, agreement.getId(),
            new SettingChangeOrderPreparationCommand.PlanSnapshot(plan.getId(), plan.getName(), plan.getUnitPrice()),
            pricingPolicy,
            deliveries
        );
    }

    private SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy(
        SettingChangePreparationResult prepared,
        Plan requestedPlan,
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> requestedConditions,
        SubscriptionPeriod currentPeriod
    ) {
        SubscriptionSetting currentSetting = settingRepository
            .findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
                prepared.subscriptionId(), SubscriptionSettingStatus.ACTIVE
            )
            .orElseThrow(() -> new IllegalStateException("현재 유효 설정을 찾을 수 없습니다."));
        List<SubscriptionDeliveryCondition> currentConditions = conditionRepository
            .findAllBySubscriptionSettingId(currentSetting.getId());
        if (isPriceNeutralChange(currentSetting, currentConditions, requestedPlan, requestedConditions)) {
            return SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER;
        }
        boolean firstDiscountPeriod = currentPeriod != null
            && orderRepository.findAllBySubscriptionPeriodId(currentPeriod.getId()).stream()
                .anyMatch(order -> order.getDiscountAmount() > 0L);
        return firstDiscountPeriod
            ? SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITH_FIRST_DISCOUNT
            : SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITHOUT_DISCOUNT;
    }

    private boolean isPriceNeutralChange(
        SubscriptionSetting currentSetting,
        List<SubscriptionDeliveryCondition> currentConditions,
        Plan requestedPlan,
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> requestedConditions
    ) {
        if (!currentSetting.getPlanId().equals(requestedPlan.getId())
            || currentConditions.size() != requestedConditions.size()) {
            return false;
        }
        return currentConditions.stream().allMatch(current -> {
            SettingChangeDraft.DeliveryCondition requested = requestedConditions.get(current.getDeliveryWeekday());
            return requested != null && current.getMealQuantity().equals(requested.mealQuantity());
        });
    }

    private List<SettingChangeOrderPreparationCommand.Delivery> deliveries(
        Long userId,
        Long subscriptionId,
        Plan plan,
        SubscriptionPeriod period,
        SettingChangeDraft draft,
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> conditions,
        Map<LocalDate, Order> replacementByDate,
        SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy
    ) {
        Set<LocalDate> holidays = holidayRepository.findAllByHolidayDateBetween(
            draft.effectiveStartDate(), period.getPeriodEndDate()
        ).stream().map(Holiday::getHolidayDate).collect(java.util.stream.Collectors.toSet());
        Map<Long, Address> addresses = new HashMap<>();
        return draft.effectiveStartDate().datesUntil(period.getPeriodEndDate().plusDays(1))
            .filter(date -> !holidays.contains(date))
            .map(date -> deliveryForDate(
                userId, subscriptionId, plan, period, date, conditions, replacementByDate, addresses, pricingPolicy
            ))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private SettingChangeOrderPreparationCommand.Delivery deliveryForDate(
        Long userId,
        Long subscriptionId,
        Plan plan,
        SubscriptionPeriod period,
        LocalDate date,
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> conditions,
        Map<LocalDate, Order> replacementByDate,
        Map<Long, Address> addresses,
        SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy
    ) {
        DeliveryWeekday weekday = java.util.Arrays.stream(DeliveryWeekday.values())
            .filter(value -> value.toDayOfWeek() == date.getDayOfWeek())
            .findFirst().orElse(null);
        if (weekday == null) return null;
        SettingChangeDraft.DeliveryCondition condition = conditions.get(weekday);
        if (condition == null) return null;
        Address address = addresses.computeIfAbsent(condition.addressId(), this::findAddress);
        if (!userId.equals(address.getUserId()) || address.getDeletedAt() != null) {
            throw new AddressNotFoundException();
        }
        Menu menu = menuRepository.findByPlanIdAndMenuSequence(plan.getId(), date.getDayOfMonth())
            .orElseThrow(() -> new IllegalStateException("변경 주문 메뉴를 찾을 수 없습니다."));
        Order target = replacementByDate.get(date);
        if (pricingPolicy == SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER
            && target == null) {
            throw new IllegalStateException("가격 비영향 변경 대상 배송일의 기존 주문을 찾을 수 없습니다.");
        }
        int revisionSequence = target != null
            ? target.getRevisionSequence() + 1
            : orderRepository.findTopBySubscriptionIdAndDeliveryDateOrderByRevisionSequenceDesc(subscriptionId, date)
                .map(order -> order.getRevisionSequence() + 1).orElse(1);
        return new SettingChangeOrderPreparationCommand.Delivery(
            period.getId(), date, revisionSequence, target == null ? null : target.getId(), menu.getId(), menu.getPlanId(),
            menu.getMenuSequence(), menu.getName(), condition.mealQuantity(),
            pricingPolicy == SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER
                ? amountSnapshot(target)
                : null,
            new SettingChangeOrderPreparationCommand.AddressSnapshot(
                address.getId(), address.getRecipientName(), address.getRecipientPhone(), address.getPostalCode(),
                address.getAddressLine1(), address.getAddressLine2(), address.getDeliveryMethodCode(),
                address.getOtherDeliveryRequest(), address.getEntrancePassword()
            ),
            toOrderTimeSlot(condition.deliveryTimeSlot())
        );
    }

    private SettingChangeOrderPreparationCommand.AmountSnapshot amountSnapshot(Order target) {
        if (target == null) {
            return null;
        }
        return new SettingChangeOrderPreparationCommand.AmountSnapshot(
            target.getMealUnitPrice(), target.getMealQuantity(), target.getMealAmount(), target.getDeliveryFee(),
            target.getDiscountAmount(), target.getActualAllocatedAmount()
        );
    }

    private Address findAddress(Long addressId) {
        return addressRepository.findById(addressId).orElseThrow(AddressNotFoundException::new);
    }

    private Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> conditionsByWeekday(SettingChangeDraft draft) {
        Map<DeliveryWeekday, SettingChangeDraft.DeliveryCondition> conditions = new EnumMap<>(DeliveryWeekday.class);
        for (SettingChangeDraft.DeliveryCondition condition : draft.deliveryConditions()) {
            if (conditions.put(condition.weekday(), condition) != null) {
                throw new IllegalArgumentException("변경 배송 요일이 중복되었습니다.");
            }
        }
        return conditions;
    }

    private OrderDeliveryTimeSlot toOrderTimeSlot(DeliveryTimeSlot slot) {
        return switch (slot) {
            case TIME_1100_1300 -> OrderDeliveryTimeSlot.TIME_1100_1300;
            case TIME_1700_1900 -> OrderDeliveryTimeSlot.TIME_1700_1900;
        };
    }
}
