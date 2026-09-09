package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 첫 결제에 사용할 실제 배송일별 주문을 준비하고 결제 결과로 상태를 확정한다. */
@Service
public class FirstOrderService {
    private final OrderRepository orderRepository;
    private final HolidayRepository holidayRepository;

    /**
     * 주문 저장소를 사용해 첫 주문 Service를 구성한다.
     *
     * @param orderRepository 주문 저장소
     * @param holidayRepository 대한민국 공식 공휴일 조회 저장소
     */
    public FirstOrderService(OrderRepository orderRepository, HolidayRepository holidayRepository) {
        this.orderRepository = orderRepository;
        this.holidayRepository = holidayRepository;
    }

    /**
     * 실제 배송일별 확정 대기 주문을 생성하고 첫 결제 총액을 반환한다.
     *
     * @param command 다른 담당 영역에서 확인한 설정·스냅샷·실제 배송일 입력
     * @return 저장 주문별 배분금액과 전체 결제금액
     */
    @Transactional
    public FirstOrderPreparationResult prepare(FirstOrderPreparationCommand command) {
        if (orderRepository.existsBySubscriptionPeriodId(command.subscriptionPeriodId())) {
            throw new IllegalStateException("First orders already exist for the subscription period");
        }

        validateDeliveries(command);
        List<Order> orders = command.deliveries().stream()
            .map(delivery -> createOrder(command, delivery))
            .toList();
        List<Order> savedOrders = orderRepository.saveAll(orders);

        long totalPaymentAmount = 0L;
        List<FirstOrderPreparationResult.OrderAmount> orderAmounts = new ArrayList<>();
        for (Order order : savedOrders) {
            totalPaymentAmount = Math.addExact(totalPaymentAmount, order.getActualAllocatedAmount());
            orderAmounts.add(new FirstOrderPreparationResult.OrderAmount(
                order.getId(),
                order.getActualAllocatedAmount()
            ));
        }

        return new FirstOrderPreparationResult(
            orderAmounts,
            totalPaymentAmount,
            command.applyFirstDiscount()
        );
    }

    /**
     * 첫 결제 성공 시 대상 확정 대기 주문을 유효 상태로 변경한다.
     *
     * @param subscriptionPeriodId 이번 첫 결제 대상 이용 기간 식별자
     * @param orderIds 이번 첫 결제로 확정할 주문 식별자 목록
     */
    @Transactional
    public void activateAfterPayment(Long subscriptionPeriodId, List<Long> orderIds) {
        updateOrders(subscriptionPeriodId, orderIds, Order::activateAfterPayment);
    }

    /**
     * 첫 결제 거절 시 대상 확정 대기 주문을 결제 실패 상태로 변경한다.
     *
     * @param subscriptionPeriodId 이번 첫 결제 대상 이용 기간 식별자
     * @param orderIds 이번 첫 결제 실패로 확정할 주문 식별자 목록
     */
    @Transactional
    public void markPaymentFailed(Long subscriptionPeriodId, List<Long> orderIds) {
        updateOrders(subscriptionPeriodId, orderIds, Order::markPaymentFailed);
    }

    private Order createOrder(
        FirstOrderPreparationCommand command,
        FirstOrderPreparationCommand.Delivery delivery
    ) {
        FirstOrderPreparationCommand.PlanSnapshot plan = command.plan();
        FirstOrderPreparationCommand.AddressSnapshot address = delivery.address();
        FirstOrderAmountCalculator.FirstOrderAmount amount = FirstOrderAmountCalculator.calculate(
            plan.mealUnitPrice(), delivery.mealQuantity(), command.applyFirstDiscount()
        );
        int revisionSequence = nextRevisionSequence(command.subscriptionId(), delivery.deliveryDate());

        return Order.awaitingConfirmationBuilder()
            .userId(command.userId())
            .subscriptionId(command.subscriptionId())
            .subscriptionPeriodId(command.subscriptionPeriodId())
            .subscriptionSettingId(command.subscriptionSettingId())
            .termsAgreementId(command.termsAgreementId())
            .planId(plan.planId())
            .addressId(address.addressId())
            .menuId(delivery.menuId())
            .deliveryDate(delivery.deliveryDate())
            .revisionSequence(revisionSequence)
            .planName(plan.planName())
            .menuName(delivery.menuName())
            .mealUnitPrice(plan.mealUnitPrice())
            .mealQuantity(delivery.mealQuantity())
            .mealAmount(amount.mealAmount())
            .deliveryFee(amount.deliveryFee())
            .discountAmount(amount.discountAmount())
            .actualAllocatedAmount(amount.actualAllocatedAmount())
            .recipientName(address.recipientName())
            .recipientPhone(address.recipientPhone())
            .postalCode(address.postalCode())
            .addressLine1(address.addressLine1())
            .addressLine2(address.addressLine2())
            .deliveryMethodCode(address.deliveryMethodCode())
            .otherDeliveryRequest(address.otherDeliveryRequest())
            .entrancePassword(address.entrancePassword())
            .deliveryTimeSlot(delivery.deliveryTimeSlot())
            .build();
    }

    private int nextRevisionSequence(Long subscriptionId, LocalDate deliveryDate) {
        return orderRepository
            .findTopBySubscriptionIdAndDeliveryDateOrderByRevisionSequenceDesc(subscriptionId, deliveryDate)
            .map(order -> Math.incrementExact(order.getRevisionSequence()))
            .orElse(1);
    }

    private void validateDeliveries(FirstOrderPreparationCommand command) {
        Set<LocalDate> deliveryDates = new HashSet<>();
        for (FirstOrderPreparationCommand.Delivery delivery : command.deliveries()) {
            if (!deliveryDates.add(delivery.deliveryDate())) {
                throw new IllegalArgumentException("A delivery date must not be duplicated");
            }
            if (delivery.deliveryDate().isBefore(command.periodStartDate())
                || delivery.deliveryDate().isAfter(command.periodEndDate())) {
                throw new IllegalArgumentException("A delivery date must be within the subscription period");
            }
            if (delivery.deliveryDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new IllegalArgumentException("An order must not be created on Sunday");
            }
            if (!command.plan().planId().equals(delivery.menuPlanId())) {
                throw new IllegalArgumentException("A menu must belong to the order plan");
            }
            if (delivery.deliveryDate().getDayOfMonth() != delivery.menuSequence()) {
                throw new IllegalArgumentException("A menu sequence must match the delivery day of month");
            }
        }
        if (!deliveryDates.contains(command.periodStartDate())) {
            throw new IllegalArgumentException("The first period start date must have an order");
        }
        if (holidayRepository.existsByHolidayDateIn(deliveryDates)) {
            throw new IllegalArgumentException("An order must not be created on a Korean public holiday");
        }
    }

    private void updateOrders(
        Long subscriptionPeriodId,
        List<Long> orderIds,
        Consumer<Order> stateChange
    ) {
        if (subscriptionPeriodId == null || subscriptionPeriodId <= 0) {
            throw new IllegalArgumentException("subscriptionPeriodId must be positive");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("orderIds must not be empty");
        }
        Set<Long> uniqueOrderIds = new HashSet<>(orderIds);
        boolean containsInvalidId = uniqueOrderIds.stream().anyMatch(id -> id == null || id <= 0);
        if (uniqueOrderIds.size() != orderIds.size() || containsInvalidId) {
            throw new IllegalArgumentException("orderIds must contain unique positive identifiers");
        }

        List<Order> orders = orderRepository.findAllBySubscriptionPeriodId(subscriptionPeriodId);
        Set<Long> foundOrderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        if (!foundOrderIds.equals(uniqueOrderIds)) {
            throw new IllegalArgumentException(
                "orderIds must exactly match all orders in the requested subscription period"
            );
        }
        boolean containsNonAwaitingOrder = orders.stream()
            .anyMatch(order -> order.getStatus() != OrderStatus.AWAITING_CONFIRMATION);
        if (containsNonAwaitingOrder) {
            throw new IllegalStateException("All orders must be awaiting confirmation");
        }
        orders.forEach(stateChange);
    }
}
