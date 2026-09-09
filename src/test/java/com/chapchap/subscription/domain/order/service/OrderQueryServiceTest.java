package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderListResponse;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.global.exception.order.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {
    private static final Long USER_ID = 10L;
    private static final String ORDER_PUBLIC_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock private OrderRepository orderRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private RefundRepository refundRepository;

    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(orderRepository, menuRepository, refundRepository);
    }

    @Test
    void 주문이_없으면_빈_목록을_반환한다() {
        when(orderRepository.findAllByUserIdOrderByDeliveryDateDescIdDesc(USER_ID))
            .thenReturn(List.of());

        assertThat(service.getOrders(USER_ID).orders()).isEmpty();
    }

    @Test
    void 주문_목록은_최소_필드만_변환한다() {
        Order recent = listOrder("11111111-1111-4111-8111-111111111111", LocalDate.of(2026, 9, 8), 17_800L);
        Order older = listOrder("22222222-2222-4222-8222-222222222222", LocalDate.of(2026, 9, 7), 8_900L);
        when(orderRepository.findAllByUserIdOrderByDeliveryDateDescIdDesc(USER_ID))
            .thenReturn(List.of(recent, older));

        OrderListResponse response = service.getOrders(USER_ID);

        assertThat(response.orders())
            .extracting(OrderListResponse.OrderItemResponse::orderId)
            .containsExactly(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"
            );
        assertThat(response.orders().getFirst().amount()).isEqualTo(17_800L);
    }

    @Test
    void 주문_상세는_주문_스냅샷과_메뉴_기준정보를_구분해_반환한다() {
        Order order = detailedOrder();
        Menu menu = menu(30L);
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.of(order));
        when(menuRepository.findById(40L)).thenReturn(Optional.of(menu));
        when(refundRepository.findByOrderId(20L)).thenReturn(Optional.empty());

        OrderDetailResponse response = service.getOrder(USER_ID, ORDER_PUBLIC_ID);

        assertThat(response.menuName()).isEqualTo("주문 당시 메뉴명");
        assertThat(response.menuDescription()).isEqualTo("현재 고정 메뉴 설명");
        assertThat(response.recipientName()).isEqualTo("주문 당시 수령인");
        assertThat(response.amount()).isEqualTo(17_800L);
        assertThat(response.refund()).isNull();
    }

    @Test
    void 배송_주문_환불이_있으면_저장된_환불_집계를_반환한다() {
        Order order = detailedOrder();
        Menu menu = menu(30L);
        Refund refund = refund();
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.of(order));
        when(menuRepository.findById(40L)).thenReturn(Optional.of(menu));
        when(refundRepository.findByOrderId(20L)).thenReturn(Optional.of(refund));

        OrderDetailResponse response = service.getOrder(USER_ID, ORDER_PUBLIC_ID);

        assertThat(response.refund()).isNotNull();
        assertThat(response.refund().refundId()).isEqualTo("650e8400-e29b-41d4-a716-446655440000");
        assertThat(response.refund().status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.refund().requestedAmount()).isEqualTo(17_800L);
        assertThat(response.refund().refundedAmount()).isEqualTo(17_800L);
        assertThat(response.refund().unprocessedAmount()).isZero();
    }

    @Test
    void 다른_고객_주문과_없는_주문은_같은_오류로_처리한다() {
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(USER_ID, ORDER_PUBLIC_ID))
            .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(menuRepository, refundRepository);
    }

    @Test
    void 잘못된_주문_공개_식별자는_DB를_조회하지_않는다() {
        assertThatThrownBy(() -> service.getOrder(USER_ID, "ORD-invalid"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository, menuRepository, refundRepository);
    }

    @Test
    void 연결된_메뉴가_없으면_기준정보_불일치로_처리한다() {
        Order order = detailedOrder();
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.of(order));
        when(menuRepository.findById(40L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(USER_ID, ORDER_PUBLIC_ID))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(refundRepository);
    }

    @Test
    void 주문과_다른_플랜의_메뉴는_반환하지_않는다() {
        Order order = detailedOrder();
        Menu menu = menu(999L);
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.of(order));
        when(menuRepository.findById(40L)).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> service.getOrder(USER_ID, ORDER_PUBLIC_ID))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(refundRepository);
    }

    @Test
    void 주문과_연결_조건이_다른_환불은_반환하지_않는다() {
        Order order = detailedOrder();
        Menu menu = menu(30L);
        Refund refund = refund();
        when(refund.getRefundType()).thenReturn(RefundType.SETTING_CHANGE_REDUCTION);
        when(orderRepository.findByPublicIdAndUserId(ORDER_PUBLIC_ID, USER_ID))
            .thenReturn(Optional.of(order));
        when(menuRepository.findById(40L)).thenReturn(Optional.of(menu));
        when(refundRepository.findByOrderId(20L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.getOrder(USER_ID, ORDER_PUBLIC_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    private Order listOrder(String publicId, LocalDate deliveryDate, Long amount) {
        Order order = mock(Order.class);
        when(order.getPublicId()).thenReturn(publicId);
        when(order.getDeliveryDate()).thenReturn(deliveryDate);
        when(order.getStatus()).thenReturn(OrderStatus.ACTIVE);
        when(order.getActualAllocatedAmount()).thenReturn(amount);
        return order;
    }

    private Order detailedOrder() {
        Order order = mock(Order.class);
        lenient().when(order.getId()).thenReturn(20L);
        lenient().when(order.getPublicId()).thenReturn(ORDER_PUBLIC_ID);
        lenient().when(order.getSubscriptionId()).thenReturn(25L);
        lenient().when(order.getPlanId()).thenReturn(30L);
        lenient().when(order.getMenuId()).thenReturn(40L);
        lenient().when(order.getDeliveryDate()).thenReturn(LocalDate.of(2026, 9, 8));
        lenient().when(order.getStatus()).thenReturn(OrderStatus.ACTIVE);
        lenient().when(order.getPlanName()).thenReturn("주문 당시 플랜명");
        lenient().when(order.getMenuName()).thenReturn("주문 당시 메뉴명");
        lenient().when(order.getMealQuantity()).thenReturn(2);
        lenient().when(order.getRecipientName()).thenReturn("주문 당시 수령인");
        lenient().when(order.getRecipientPhone()).thenReturn("010-0000-0000");
        lenient().when(order.getPostalCode()).thenReturn("00000");
        lenient().when(order.getAddressLine1()).thenReturn("대구광역시");
        lenient().when(order.getAddressLine2()).thenReturn("101동 101호");
        lenient().when(order.getDeliveryMethodCode()).thenReturn("DOORSTEP");
        lenient().when(order.getDeliveryTimeSlot()).thenReturn(OrderDeliveryTimeSlot.TIME_1100_1300);
        lenient().when(order.getActualAllocatedAmount()).thenReturn(17_800L);
        return order;
    }

    private Menu menu(Long planId) {
        Menu menu = mock(Menu.class);
        lenient().when(menu.getPlanId()).thenReturn(planId);
        lenient().when(menu.getMenuSequence()).thenReturn(8);
        lenient().when(menu.getDescription()).thenReturn("현재 고정 메뉴 설명");
        lenient().when(menu.getAllergenInfo()).thenReturn("알레르기 정보");
        lenient().when(menu.getNutritionInfo()).thenReturn("영양 정보");
        lenient().when(menu.getIngredientInfo()).thenReturn("원재료 정보");
        return menu;
    }

    private Refund refund() {
        Refund refund = mock(Refund.class);
        lenient().when(refund.getOrderId()).thenReturn(20L);
        lenient().when(refund.getSubscriptionId()).thenReturn(25L);
        lenient().when(refund.getRefundType()).thenReturn(RefundType.DELIVERY_PARTIAL_CANCELLATION);
        lenient().when(refund.getPublicId()).thenReturn("650e8400-e29b-41d4-a716-446655440000");
        lenient().when(refund.getStatus()).thenReturn(RefundStatus.COMPLETED);
        lenient().when(refund.getRefundAmount()).thenReturn(17_800L);
        lenient().when(refund.getSuccessfulRefundAmount()).thenReturn(17_800L);
        lenient().when(refund.getUnprocessedAmount()).thenReturn(0L);
        return refund;
    }
}
