package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderListResponse;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.global.exception.order.OrderNotFoundException;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 고객의 주문 목록과 주문 당시 상세 정보를 읽기 전용으로 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final RefundRepository refundRepository;

    public OrderListResponse getOrders(Long userId) {
        validateUserId(userId);
        return new OrderListResponse(
            orderRepository.findAllByUserIdOrderByDeliveryDateDescIdDesc(userId)
                .stream()
                .map(this::toListItem)
                .toList()
        );
    }

    public OrderDetailResponse getOrder(Long userId, String orderId) {
        validateUserId(userId);
        validateOrderId(orderId);
        Order order = orderRepository.findByPublicIdAndUserId(orderId, userId)
            .orElseThrow(OrderNotFoundException::new);
        Long internalOrderId = requirePositive(order.getId(), "주문");
        Menu menu = menuRepository.findById(requirePositive(order.getMenuId(), "메뉴 참조"))
            .orElseThrow(this::inconsistentData);
        validateMenuReference(order, menu);

        OrderDetailResponse.RefundResponse refund = refundRepository.findByOrderId(internalOrderId)
            .map(value -> toRefund(order, value))
            .orElse(null);

        return new OrderDetailResponse(
            order.getPublicId(),
            order.getDeliveryDate(),
            order.getStatus(),
            order.getPlanName(),
            order.getMenuName(),
            order.getMealQuantity(),
            menu.getDescription(),
            menu.getAllergenInfo(),
            menu.getNutritionInfo(),
            menu.getIngredientInfo(),
            order.getRecipientName(),
            order.getRecipientPhone(),
            order.getPostalCode(),
            order.getAddressLine1(),
            order.getAddressLine2(),
            order.getDeliveryMethodCode(),
            order.getOtherDeliveryRequest(),
            order.getDeliveryTimeSlot(),
            order.getActualAllocatedAmount(),
            refund
        );
    }

    private OrderListResponse.OrderItemResponse toListItem(Order order) {
        return new OrderListResponse.OrderItemResponse(
            order.getPublicId(),
            order.getDeliveryDate(),
            order.getStatus(),
            order.getActualAllocatedAmount()
        );
    }

    private void validateMenuReference(Order order, Menu menu) {
        if (!order.getPlanId().equals(menu.getPlanId())
            || order.getDeliveryDate() == null
            || !Integer.valueOf(order.getDeliveryDate().getDayOfMonth()).equals(menu.getMenuSequence())) {
            throw inconsistentData();
        }
    }

    private OrderDetailResponse.RefundResponse toRefund(Order order, Refund refund) {
        if (!order.getId().equals(refund.getOrderId())
            || !order.getSubscriptionId().equals(refund.getSubscriptionId())
            || refund.getRefundType() != RefundType.DELIVERY_PARTIAL_CANCELLATION
            || !order.getActualAllocatedAmount().equals(refund.getRefundAmount())) {
            throw inconsistentData();
        }
        return new OrderDetailResponse.RefundResponse(
            refund.getPublicId(),
            refund.getStatus(),
            refund.getRefundAmount(),
            refund.getSuccessfulRefundAmount(),
            refund.getUnprocessedAmount()
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }
    }

    private void validateOrderId(String orderId) {
        if (!PublicIdFormat.isUuidV4(orderId)) {
            throw new IllegalArgumentException("유효하지 않은 주문 공개 식별자입니다.");
        }
    }

    private Long requirePositive(Long value, String target) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(target + " 데이터가 올바르지 않습니다.");
        }
        return value;
    }

    private IllegalStateException inconsistentData() {
        return new IllegalStateException("주문 상세 기준 데이터가 올바르지 않습니다.");
    }
}
