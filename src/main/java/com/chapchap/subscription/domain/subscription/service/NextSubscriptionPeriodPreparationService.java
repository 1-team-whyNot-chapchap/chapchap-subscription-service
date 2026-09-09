package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 현재 이용 기간 종료일에 다음 28일 기간과 결제 전 주문을 한 번 준비한다. */
@Service
public class NextSubscriptionPeriodPreparationService {
    private static final long DELIVERY_FEE = 3_000L;
    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionDeliveryConditionRepository conditionRepository;
    private final PlanRepository planRepository;
    private final MenuRepository menuRepository;
    private final AddressRepository addressRepository;
    private final HolidayRepository holidayRepository;
    private final TermsService termsService;
    private final OrderRepository orderRepository;

    public NextSubscriptionPeriodPreparationService(SubscriptionPeriodRepository periodRepository, SubscriptionRepository subscriptionRepository, SubscriptionSettingRepository settingRepository, SubscriptionDeliveryConditionRepository conditionRepository, PlanRepository planRepository, MenuRepository menuRepository, AddressRepository addressRepository, HolidayRepository holidayRepository, TermsService termsService, OrderRepository orderRepository) {
        this.periodRepository = periodRepository; this.subscriptionRepository = subscriptionRepository;
        this.settingRepository = settingRepository; this.conditionRepository = conditionRepository;
        this.planRepository = planRepository; this.menuRepository = menuRepository; this.addressRepository = addressRepository;
        this.holidayRepository = holidayRepository; this.termsService = termsService; this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueCurrentPeriodIds(LocalDate today) {
        return periodRepository.findAllByStatusAndPeriodEndDate(SubscriptionPeriodStatus.IN_PROGRESS, today)
            .stream().map(SubscriptionPeriod::getId).toList();
    }

    /** 한 구독의 다음 기간과 주문을 만들거나 이미 준비된 동일 스냅샷을 반환한다. */
    @Transactional
    public Optional<PreparedNextSubscriptionPeriod> prepareIfDue(
        Long currentId,
        LocalDate today,
        LocalDateTime referenceAt
    ) {
        SubscriptionPeriod current = periodRepository.findWithLockById(currentId).orElse(null);
        if (current == null || current.getStatus() != SubscriptionPeriodStatus.IN_PROGRESS
            || !current.getPeriodEndDate().equals(today)) return Optional.empty();
        Subscription subscription = subscriptionRepository.findWithLockById(current.getSubscriptionId()).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) return Optional.empty();
        int nextSequence = current.getPeriodSequence() + 1;
        Optional<SubscriptionPeriod> latest = periodRepository
            .findTopBySubscriptionIdOrderByPeriodSequenceDesc(subscription.getId());
        if (latest.isPresent() && latest.get().getPeriodSequence() >= nextSequence) {
            SubscriptionPeriod existing = latest.get();
            if (existing.getPeriodSequence() != nextSequence
                || existing.getStatus() != SubscriptionPeriodStatus.AWAITING_CONFIRMATION) {
                return Optional.empty();
            }
            return Optional.of(toPrepared(subscription, existing,
                orderRepository.findAllBySubscriptionPeriodId(existing.getId())));
        }

        LocalDate nextStart = current.getPeriodEndDate().plusDays(1);
        SubscriptionSetting setting = settingRepository.findApplicableSettings(subscription.getId(), SubscriptionSettingStatus.ACTIVE, current.getPeriodEndDate())
            .stream().findFirst().orElseThrow(() -> new IllegalStateException("Active subscription setting is missing"));
        List<SubscriptionDeliveryCondition> conditions = conditionRepository.findAllBySubscriptionSettingId(setting.getId());
        if (conditions.isEmpty()) throw new IllegalStateException("Active delivery conditions are missing");
        SubscriptionPeriod next = periodRepository.save(SubscriptionPeriod.createAwaitingConfirmation(subscription.getId(), nextSequence, nextStart, referenceAt));
        Plan plan = planRepository.findById(setting.getPlanId()).orElseThrow(() -> new IllegalStateException("Plan is missing"));
        UserTermsAgreement agreement = termsService.requireCurrentAgreement(subscription.getUserId());
        Set<LocalDate> holidays = new HashSet<>(holidayRepository.findAllByHolidayDateBetween(nextStart, next.getPeriodEndDate()).stream().map(Holiday::getHolidayDate).toList());
        List<Order> orders = new ArrayList<>();
        for (LocalDate date = nextStart; !date.isAfter(next.getPeriodEndDate()); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(date)) continue;
            DayOfWeek deliveryDayOfWeek = date.getDayOfWeek();
            SubscriptionDeliveryCondition condition = conditions.stream().filter(c -> c.getDeliveryWeekday().toDayOfWeek() == deliveryDayOfWeek).findFirst().orElse(null);
            if (condition == null) continue;
            Menu menu = menuRepository.findByPlanIdAndMenuSequence(plan.getId(), date.getDayOfMonth()).orElseThrow(() -> new IllegalStateException("Plan menu is missing"));
            Address address = addressRepository.findById(condition.getAddressId()).orElseThrow(() -> new IllegalStateException("Delivery address is missing"));
            long mealAmount = Math.multiplyExact(plan.getUnitPrice(), condition.getMealQuantity().longValue());
            orders.add(Order.awaitingConfirmationBuilder().userId(subscription.getUserId()).subscriptionId(subscription.getId()).subscriptionPeriodId(next.getId()).subscriptionSettingId(setting.getId()).termsAgreementId(agreement.getId()).planId(plan.getId()).addressId(address.getId()).menuId(menu.getId()).deliveryDate(date).revisionSequence(1).planName(plan.getName()).menuName(menu.getName()).mealUnitPrice(plan.getUnitPrice()).mealQuantity(condition.getMealQuantity()).mealAmount(mealAmount).deliveryFee(DELIVERY_FEE).discountAmount(0L).actualAllocatedAmount(Math.addExact(mealAmount, DELIVERY_FEE)).recipientName(address.getRecipientName()).recipientPhone(address.getRecipientPhone()).postalCode(address.getPostalCode()).addressLine1(address.getAddressLine1()).addressLine2(address.getAddressLine2()).deliveryMethodCode(address.getDeliveryMethodCode()).otherDeliveryRequest(address.getOtherDeliveryRequest()).entrancePassword(address.getEntrancePassword()).deliveryTimeSlot(toOrderTimeSlot(condition.getDeliveryTimeSlot())).build());
        }
        List<Order> savedOrders = orderRepository.saveAll(orders);
        return Optional.of(toPrepared(subscription, next, savedOrders == null ? orders : savedOrders));
    }

    private PreparedNextSubscriptionPeriod toPrepared(
        Subscription subscription,
        SubscriptionPeriod period,
        List<Order> orders
    ) {
        List<PaymentAllocationCommand> allocations = orders.stream()
            .map(order -> new PaymentAllocationCommand(order.getId(), order.getActualAllocatedAmount()))
            .toList();
        long totalAmount = allocations.stream()
            .mapToLong(PaymentAllocationCommand::allocationAmount)
            .reduce(0L, Math::addExact);
        if (allocations.isEmpty() || totalAmount <= 0) {
            throw new IllegalStateException("Regular payment orders are missing");
        }
        return new PreparedNextSubscriptionPeriod(
            subscription.getUserId(), subscription.getId(), period.getId(), period.getPeriodStartDate(),
            period.getPeriodEndDate(), period.getCalculationReferenceAt(), totalAmount, allocations
        );
    }

    private OrderDeliveryTimeSlot toOrderTimeSlot(DeliveryTimeSlot slot) {
        return slot == DeliveryTimeSlot.TIME_1100_1300 ? OrderDeliveryTimeSlot.TIME_1100_1300 : OrderDeliveryTimeSlot.TIME_1700_1900;
    }
}
