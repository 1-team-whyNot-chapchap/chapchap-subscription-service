package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 설정 변경 적용 후보의 주문을 변경 대기 상태로 저장한다. */
@Service
public class SettingChangeOrderService {
    private static final long DELIVERY_FEE = 3_000L;

    private final OrderRepository orderRepository;
    private final HolidayRepository holidayRepository;

    public SettingChangeOrderService(OrderRepository orderRepository, HolidayRepository holidayRepository) {
        this.orderRepository = orderRepository;
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public List<Order> prepare(SettingChangeOrderPreparationCommand command) {
        if (command.deliveries().isEmpty()) {
            return List.of();
        }
        validate(command);
        List<Order> orders = command.deliveries().stream()
            .map(delivery -> createOrder(command, delivery))
            .toList();
        return orderRepository.saveAll(orders);
    }

    private Order createOrder(
        SettingChangeOrderPreparationCommand command,
        SettingChangeOrderPreparationCommand.Delivery delivery
    ) {
        SettingChangeOrderPreparationCommand.PlanSnapshot plan = command.plan();
        SettingChangeOrderPreparationCommand.AddressSnapshot address = delivery.address();
        OrderAmounts amounts = amounts(command.pricingPolicy(), plan, delivery);
        return Order.createChangePending(
            command.userId(), command.subscriptionId(), delivery.subscriptionPeriodId(), command.subscriptionSettingId(),
            command.termsAgreementId(), plan.planId(), address.addressId(), delivery.menuId(), delivery.deliveryDate(),
            delivery.revisionSequence(), delivery.replacementTargetOrderId(), plan.planName(), delivery.menuName(),
            amounts.mealUnitPrice(), amounts.mealQuantity(), amounts.mealAmount(), amounts.deliveryFee(),
            amounts.discountAmount(), amounts.actualAllocatedAmount(),
            address.recipientName(), address.recipientPhone(), address.postalCode(), address.addressLine1(),
            address.addressLine2(), address.deliveryMethodCode(), address.otherDeliveryRequest(),
            address.entrancePassword(), delivery.deliveryTimeSlot()
        );
    }

    private OrderAmounts amounts(
        SettingChangeOrderPreparationCommand.PricingPolicy pricingPolicy,
        SettingChangeOrderPreparationCommand.PlanSnapshot plan,
        SettingChangeOrderPreparationCommand.Delivery delivery
    ) {
        if (pricingPolicy == SettingChangeOrderPreparationCommand.PricingPolicy.PRESERVE_REPLACED_ORDER) {
            SettingChangeOrderPreparationCommand.AmountSnapshot snapshot = delivery.replacementAmount();
            if (snapshot == null || !delivery.mealQuantity().equals(snapshot.mealQuantity())) {
                throw new IllegalArgumentException("가격 비영향 변경은 같은 배송일 기존 주문의 금액 정보가 필요합니다.");
            }
            return new OrderAmounts(
                snapshot.mealUnitPrice(), snapshot.mealQuantity(), snapshot.mealAmount(), snapshot.deliveryFee(),
                snapshot.discountAmount(), snapshot.actualAllocatedAmount()
            );
        }

        long mealAmount = Math.multiplyExact(plan.mealUnitPrice(), delivery.mealQuantity().longValue());
        long discountAmount = pricingPolicy
            == SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITH_FIRST_DISCOUNT
            ? FirstSubscriptionDiscountCalculator.calculate(plan.mealUnitPrice())
            : 0L;
        long actualAllocatedAmount = Math.subtractExact(
            Math.addExact(mealAmount, DELIVERY_FEE), discountAmount
        );
        return new OrderAmounts(
            plan.mealUnitPrice(), delivery.mealQuantity(), mealAmount, DELIVERY_FEE,
            discountAmount, actualAllocatedAmount
        );
    }

    private record OrderAmounts(
        Long mealUnitPrice,
        Integer mealQuantity,
        Long mealAmount,
        Long deliveryFee,
        Long discountAmount,
        Long actualAllocatedAmount
    ) {
    }

    private void validate(SettingChangeOrderPreparationCommand command) {
        if (command.subscriptionSettingId() == null || command.subscriptionSettingId() <= 0) {
            throw new IllegalArgumentException("저장된 설정 식별자가 필요합니다.");
        }
        Set<LocalDate> dates = new HashSet<>();
        Set<Long> replacementTargets = new HashSet<>();
        for (SettingChangeOrderPreparationCommand.Delivery delivery : command.deliveries()) {
            if (delivery.subscriptionPeriodId() == null || delivery.subscriptionPeriodId() <= 0
                || delivery.deliveryDate() == null || delivery.menuId() == null || delivery.menuPlanId() == null
                || delivery.menuSequence() == null || delivery.address() == null || delivery.deliveryTimeSlot() == null) {
                throw new IllegalArgumentException("설정 변경 주문 배송 정보가 올바르지 않습니다.");
            }
            if (!dates.add(delivery.deliveryDate())) {
                throw new IllegalArgumentException("같은 배송일의 변경 주문은 하나만 만들 수 있습니다.");
            }
            if (delivery.deliveryDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new IllegalArgumentException("일요일에는 주문을 만들 수 없습니다.");
            }
            if (!command.plan().planId().equals(delivery.menuPlanId())
                || delivery.deliveryDate().getDayOfMonth() != delivery.menuSequence()) {
                throw new IllegalArgumentException("변경 주문의 메뉴 정보가 플랜 또는 배송일과 일치하지 않습니다.");
            }
            if (delivery.replacementTargetOrderId() != null && !replacementTargets.add(delivery.replacementTargetOrderId())) {
                throw new IllegalArgumentException("기존 주문 하나는 변경 주문 하나만 대체할 수 있습니다.");
            }
        }
        if (holidayRepository.existsByHolidayDateIn(dates)) {
            throw new IllegalArgumentException("공휴일에는 주문을 만들 수 없습니다.");
        }
    }
}
