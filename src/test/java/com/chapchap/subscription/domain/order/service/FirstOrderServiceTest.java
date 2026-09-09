package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstOrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @InjectMocks
    private FirstOrderService service;

    @Test
    void 첫_할인은_배송일별_도시락_한_개에만_적용하고_전체_결제금액을_합산한다() {
        FirstOrderPreparationCommand command = command(
            true,
            deliveries(
                delivery(LocalDate.of(2026, 9, 7), 7, 2),
                delivery(LocalDate.of(2026, 9, 9), 9, 3)
            )
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        assignIdsWhenSaved();

        FirstOrderPreparationResult result = service.prepare(command);

        assertThat(result.totalPaymentAmount()).isEqualTo(45_160L);
        assertThat(result.firstDiscountApplied()).isTrue();
        assertThat(result.orders())
            .extracting(FirstOrderPreparationResult.OrderAmount::actualAllocatedAmount)
            .containsExactly(18_130L, 27_030L);

        ArgumentCaptor<List<Order>> captor = orderListCaptor();
        verify(orderRepository).saveAll(captor.capture());
        Order firstOrder = captor.getValue().get(0);
        assertThat(firstOrder.getMealAmount()).isEqualTo(17_800L);
        assertThat(firstOrder.getDeliveryFee()).isEqualTo(3_000L);
        assertThat(firstOrder.getDiscountAmount()).isEqualTo(2_670L);
        assertThat(firstOrder.getActualAllocatedAmount()).isEqualTo(18_130L);
        assertThat(firstOrder.getRevisionSequence()).isEqualTo(1);
        assertThat(firstOrder.getReplacementTargetOrderId()).isNull();
        assertThat(firstOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
        assertThat(firstOrder.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.NOT_SENT);
        assertThat(PublicIdFormat.isUuidV4(firstOrder.getPublicId())).isTrue();
    }

    @Test
    void 같은_구독과_배송일의_기존_최대_순번이_1이면_재신청_주문은_2로_생성한다() {
        LocalDate deliveryDate = LocalDate.of(2026, 9, 7);
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(deliveryDate, 7, 1))
        );
        Order previousOrder = awaitingOrder();
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        when(orderRepository.findTopBySubscriptionIdAndDeliveryDateOrderByRevisionSequenceDesc(20L, deliveryDate))
            .thenReturn(Optional.of(previousOrder));
        assignIdsWhenSaved();

        service.prepare(command);

        ArgumentCaptor<List<Order>> captor = orderListCaptor();
        verify(orderRepository).saveAll(captor.capture());
        Order reapplicationOrder = captor.getValue().getFirst();
        assertThat(reapplicationOrder.getRevisionSequence()).isEqualTo(2);
        assertThat(reapplicationOrder.getReplacementTargetOrderId()).isNull();
        assertThat(previousOrder.getRevisionSequence()).isEqualTo(1);
    }

    @Test
    void 같은_구독과_배송일의_기존_최대_순번이_2이면_재신청_주문은_3으로_생성한다() {
        LocalDate deliveryDate = LocalDate.of(2026, 9, 7);
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(deliveryDate, 7, 1))
        );
        Order previousOrder = awaitingOrder();
        ReflectionTestUtils.setField(previousOrder, "revisionSequence", 2);
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        when(orderRepository.findTopBySubscriptionIdAndDeliveryDateOrderByRevisionSequenceDesc(20L, deliveryDate))
            .thenReturn(Optional.of(previousOrder));
        assignIdsWhenSaved();

        service.prepare(command);

        ArgumentCaptor<List<Order>> captor = orderListCaptor();
        verify(orderRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getRevisionSequence()).isEqualTo(3);
        assertThat(previousOrder.getRevisionSequence()).isEqualTo(2);
    }

    @Test
    void 할인_미적용_주문은_도시락_금액과_배송비를_전액_합산한다() {
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 2))
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        assignIdsWhenSaved();

        FirstOrderPreparationResult result = service.prepare(command);

        assertThat(result.totalPaymentAmount()).isEqualTo(20_800L);
        assertThat(result.firstDiscountApplied()).isFalse();
    }

    @Test
    void 배송일마다_선택한_배송지와_시간대를_각_주문에_따로_보존한다() {
        FirstOrderPreparationCommand.AddressSnapshot mondayAddress = address(
            80L, "월요일 수령인", "대구광역시 중구 월요일로 1"
        );
        FirstOrderPreparationCommand.AddressSnapshot wednesdayAddress = address(
            81L, "수요일 수령인", "대구광역시 중구 수요일로 1"
        );
        FirstOrderPreparationCommand command = new FirstOrderPreparationCommand(
            1L,
            20L,
            30L,
            40L,
            50L,
            new FirstOrderPreparationCommand.PlanSnapshot(60L, "가정식", 8_900L),
            false,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            List.of(
                new FirstOrderPreparationCommand.Delivery(
                    LocalDate.of(2026, 9, 7), 77L, 60L, 7, "월요일 메뉴", 1,
                    mondayAddress, OrderDeliveryTimeSlot.TIME_1100_1300
                ),
                new FirstOrderPreparationCommand.Delivery(
                    LocalDate.of(2026, 9, 9), 79L, 60L, 9, "수요일 메뉴", 2,
                    wednesdayAddress, OrderDeliveryTimeSlot.TIME_1700_1900
                )
            )
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        assignIdsWhenSaved();

        service.prepare(command);

        ArgumentCaptor<List<Order>> captor = orderListCaptor();
        verify(orderRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .extracting(Order::getAddressId, Order::getRecipientName, Order::getDeliveryTimeSlot)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    80L, "월요일 수령인", OrderDeliveryTimeSlot.TIME_1100_1300
                ),
                org.assertj.core.groups.Tuple.tuple(
                    81L, "수요일 수령인", OrderDeliveryTimeSlot.TIME_1700_1900
                )
            );
    }

    @Test
    void 같은_배송일이_두_번_들어오면_주문을_저장하지_않는다() {
        LocalDate date = LocalDate.of(2026, 9, 7);
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(date, 7, 1), delivery(date, 7, 2))
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicated");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 일요일에는_주문을_생성하지_않는다() {
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 13), 13, 1))
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sunday");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 대한민국_공휴일에는_주문을_생성하지_않는다() {
        LocalDate publicHoliday = LocalDate.of(2026, 9, 25);
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(
                delivery(LocalDate.of(2026, 9, 7), 7, 1),
                delivery(publicHoliday, 25, 1)
            )
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);
        when(holidayRepository.existsByHolidayDateIn(org.mockito.ArgumentMatchers.anySet()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("public holiday");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 이용_기간_밖의_배송일은_거절한다() {
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 10, 5), 5, 1))
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("within");
    }

    @Test
    void 첫_이용_기간_시작일의_주문이_없으면_거절한다() {
        FirstOrderPreparationCommand command = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 9), 9, 1))
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("start date");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 메뉴의_플랜이_주문_플랜과_다르면_거절한다() {
        FirstOrderPreparationCommand.Delivery delivery = new FirstOrderPreparationCommand.Delivery(
            LocalDate.of(2026, 9, 7),
            70L,
            99L,
            7,
            "닭가슴살 도시락",
            1,
            address(80L, "홍길동", "대구광역시 중구 중앙대로 1"),
            OrderDeliveryTimeSlot.TIME_1100_1300
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command(false, deliveries(delivery))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("plan");
    }

    @Test
    void 메뉴_순번이_배송일의_일자와_다르면_거절한다() {
        FirstOrderPreparationCommand.Delivery delivery = delivery(LocalDate.of(2026, 9, 7), 8, 1);
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command(false, deliveries(delivery))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sequence");
    }

    @Test
    void 같은_이용_기간의_최초_주문이_이미_있으면_다시_생성하지_않는다() {
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(true);

        assertThatThrownBy(() -> service.prepare(command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 1))
        ))).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exist");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 도시락_금액_계산이_long_범위를_넘으면_주문을_저장하지_않는다() {
        FirstOrderPreparationCommand original = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 2))
        );
        FirstOrderPreparationCommand command = withPriceAndDeliveryMethod(
            original,
            Long.MAX_VALUE,
            "DIRECT",
            null
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(ArithmeticException.class);

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void OTHER_배달_방식에_기타_요청이_없으면_주문을_저장하지_않는다() {
        FirstOrderPreparationCommand original = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 1))
        );
        FirstOrderPreparationCommand command = withPriceAndDeliveryMethod(
            original,
            original.plan().mealUnitPrice(),
            "OTHER",
            null
        );
        when(orderRepository.existsBySubscriptionPeriodId(30L)).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("otherDeliveryRequest");

        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    void 첫_결제_성공은_주문을_ACTIVE로_바꾸고_스냅샷을_유지한다() {
        Order order = awaitingOrder();
        ReflectionTestUtils.setField(order, "id", 1L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(order));

        service.activateAfterPayment(30L, List.of(1L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(order.getMealUnitPrice()).isEqualTo(8_900L);
        assertThat(order.getRecipientName()).isEqualTo("홍길동");
        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.NOT_SENT);
    }

    @Test
    void 첫_결제_거절은_주문을_PAYMENT_FAILED로_바꾼다() {
        Order order = awaitingOrder();
        ReflectionTestUtils.setField(order, "id", 1L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(order));

        service.markPaymentFailed(30L, List.of(1L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void 이미_확정된_주문은_다른_결제_결과로_다시_변경하지_않는다() {
        Order order = awaitingOrder();
        order.activateAfterPayment();
        ReflectionTestUtils.setField(order, "id", 1L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(order));

        assertThatThrownBy(() -> service.markPaymentFailed(30L, List.of(1L)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACTIVE);
    }

    @Test
    void 주문_입력의_toString은_배송_개인정보를_노출하지_않는다() {
        FirstOrderPreparationCommand source = command(
            false,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 1))
        );
        FirstOrderPreparationCommand.Delivery sourceDelivery = source.deliveries().get(0);
        FirstOrderPreparationCommand.AddressSnapshot sensitiveAddress =
            new FirstOrderPreparationCommand.AddressSnapshot(
                sourceDelivery.address().addressId(),
                "민감한 수령인",
                "010-9999-8888",
                sourceDelivery.address().postalCode(),
                "민감한 기본 주소",
                "민감한 상세 주소",
                "OTHER",
                "민감한 배송 요청",
                "민감한 공동현관 비밀번호"
            );
        FirstOrderPreparationCommand command = new FirstOrderPreparationCommand(
            source.userId(),
            source.subscriptionId(),
            source.subscriptionPeriodId(),
            source.subscriptionSettingId(),
            source.termsAgreementId(),
            source.plan(),
            source.applyFirstDiscount(),
            source.periodStartDate(),
            source.periodEndDate(),
            List.of(new FirstOrderPreparationCommand.Delivery(
                sourceDelivery.deliveryDate(),
                sourceDelivery.menuId(),
                sourceDelivery.menuPlanId(),
                sourceDelivery.menuSequence(),
                sourceDelivery.menuName(),
                sourceDelivery.mealQuantity(),
                sensitiveAddress,
                sourceDelivery.deliveryTimeSlot()
            ))
        );

        assertThat(command.toString())
            .contains("subscriptionPeriodId=30", "deliveryCount=1")
            .doesNotContain(
                "민감한 수령인",
                "010-9999-8888",
                "민감한 기본 주소",
                "민감한 상세 주소",
                "민감한 배송 요청",
                "민감한 공동현관 비밀번호"
            );
        assertThat(command.deliveries().get(0).address().toString())
            .contains("addressId=80", "deliveryMethodCode=OTHER")
            .doesNotContain(
                "민감한 수령인",
                "010-9999-8888",
                "민감한 기본 주소",
                "민감한 상세 주소",
                "민감한 배송 요청",
                "민감한 공동현관 비밀번호"
            );
    }

    @Test
    void 다른_이용_기간의_주문이_섞이면_어느_주문도_활성화하지_않는다() {
        Order requestedPeriodOrder = awaitingOrder();
        ReflectionTestUtils.setField(requestedPeriodOrder, "id", 1L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(requestedPeriodOrder));

        assertThatThrownBy(() -> service.activateAfterPayment(30L, List.of(1L, 2L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subscription period");

        assertThat(requestedPeriodOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void 이용_기간의_일부_주문_ID가_누락되면_어느_주문도_활성화하지_않는다() {
        Order firstOrder = awaitingOrder();
        Order secondOrder = awaitingOrder();
        ReflectionTestUtils.setField(firstOrder, "id", 1L);
        ReflectionTestUtils.setField(secondOrder, "id", 2L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(firstOrder, secondOrder));

        assertThatThrownBy(() -> service.activateAfterPayment(30L, List.of(1L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly match");

        assertThat(firstOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
        assertThat(secondOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void 확정_대기가_아닌_주문이_섞이면_어느_주문도_실패로_변경하지_않는다() {
        Order awaitingOrder = awaitingOrder();
        Order activeOrder = awaitingOrder();
        activeOrder.activateAfterPayment();
        ReflectionTestUtils.setField(awaitingOrder, "id", 1L);
        ReflectionTestUtils.setField(activeOrder, "id", 2L);
        when(orderRepository.findAllBySubscriptionPeriodId(30L))
            .thenReturn(List.of(awaitingOrder, activeOrder));

        assertThatThrownBy(() -> service.markPaymentFailed(30L, List.of(1L, 2L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("awaiting confirmation");

        assertThat(awaitingOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);
        assertThat(activeOrder.getStatus()).isEqualTo(OrderStatus.ACTIVE);
    }

    private void assignIdsWhenSaved() {
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Order> orders = invocation.getArgument(0);
            for (int index = 0; index < orders.size(); index++) {
                ReflectionTestUtils.setField(orders.get(index), "id", (long) index + 1L);
            }
            return orders;
        });
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Order>> orderListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private Order awaitingOrder() {
        FirstOrderPreparationCommand command = command(
            true,
            deliveries(delivery(LocalDate.of(2026, 9, 7), 7, 2))
        );
        FirstOrderPreparationCommand.Delivery delivery = command.deliveries().get(0);
        FirstOrderPreparationCommand.PlanSnapshot plan = command.plan();
        FirstOrderPreparationCommand.AddressSnapshot address = delivery.address();
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
            .revisionSequence(1)
            .planName(plan.planName())
            .menuName(delivery.menuName())
            .mealUnitPrice(plan.mealUnitPrice())
            .mealQuantity(delivery.mealQuantity())
            .mealAmount(17_800L)
            .deliveryFee(3_000L)
            .discountAmount(2_670L)
            .actualAllocatedAmount(18_130L)
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

    private FirstOrderPreparationCommand command(
        boolean applyFirstDiscount,
        List<FirstOrderPreparationCommand.Delivery> deliveries
    ) {
        return new FirstOrderPreparationCommand(
            1L,
            20L,
            30L,
            40L,
            50L,
            new FirstOrderPreparationCommand.PlanSnapshot(60L, "가정식", 8_900L),
            applyFirstDiscount,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            deliveries
        );
    }

    private FirstOrderPreparationCommand withPriceAndDeliveryMethod(
        FirstOrderPreparationCommand source,
        long mealUnitPrice,
        String deliveryMethodCode,
        String otherDeliveryRequest
    ) {
        List<FirstOrderPreparationCommand.Delivery> updatedDeliveries = source.deliveries().stream()
            .map(delivery -> {
                FirstOrderPreparationCommand.AddressSnapshot sourceAddress = delivery.address();
                FirstOrderPreparationCommand.AddressSnapshot updatedAddress =
                    new FirstOrderPreparationCommand.AddressSnapshot(
                        sourceAddress.addressId(),
                        sourceAddress.recipientName(),
                        sourceAddress.recipientPhone(),
                        sourceAddress.postalCode(),
                        sourceAddress.addressLine1(),
                        sourceAddress.addressLine2(),
                        deliveryMethodCode,
                        otherDeliveryRequest,
                        sourceAddress.entrancePassword()
                    );
                return new FirstOrderPreparationCommand.Delivery(
                    delivery.deliveryDate(),
                    delivery.menuId(),
                    delivery.menuPlanId(),
                    delivery.menuSequence(),
                    delivery.menuName(),
                    delivery.mealQuantity(),
                    updatedAddress,
                    delivery.deliveryTimeSlot()
                );
            })
            .toList();
        return new FirstOrderPreparationCommand(
            source.userId(),
            source.subscriptionId(),
            source.subscriptionPeriodId(),
            source.subscriptionSettingId(),
            source.termsAgreementId(),
            new FirstOrderPreparationCommand.PlanSnapshot(
                source.plan().planId(),
                source.plan().planName(),
                mealUnitPrice
            ),
            source.applyFirstDiscount(),
            source.periodStartDate(),
            source.periodEndDate(),
            updatedDeliveries
        );
    }

    private FirstOrderPreparationCommand.Delivery delivery(
        LocalDate deliveryDate,
        int menuSequence,
        int quantity
    ) {
        return new FirstOrderPreparationCommand.Delivery(
            deliveryDate,
            70L + menuSequence,
            60L,
            menuSequence,
            "닭가슴살 도시락",
            quantity,
            address(80L, "홍길동", "대구광역시 중구 중앙대로 1"),
            OrderDeliveryTimeSlot.TIME_1100_1300
        );
    }

    private FirstOrderPreparationCommand.AddressSnapshot address(
        Long addressId,
        String recipientName,
        String addressLine1
    ) {
        return new FirstOrderPreparationCommand.AddressSnapshot(
            addressId,
            recipientName,
            "010-1234-5678",
            "12345",
            addressLine1,
            null,
            "DIRECT",
            null,
            null
        );
    }

    @SafeVarargs
    private final List<FirstOrderPreparationCommand.Delivery> deliveries(
        FirstOrderPreparationCommand.Delivery... deliveries
    ) {
        return List.of(deliveries);
    }
}
