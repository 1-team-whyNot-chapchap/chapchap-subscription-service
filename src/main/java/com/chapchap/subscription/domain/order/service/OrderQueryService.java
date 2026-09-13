package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderCalendarResponse;
import com.chapchap.subscription.domain.order.response.OrderHistoryResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/** 인증 고객의 주문 목록과 주문 당시 상세 정보를 읽기 전용으로 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {
    private static final int ORDER_HISTORY_PAGE_SIZE = 3;
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

    public OrderCalendarResponse getOrderCalendar(Long userId, String month) {
        validateUserId(userId);
        YearMonth requestedMonth = requireMonth(month);
        return new OrderCalendarResponse(
            requestedMonth.toString(),
            orderRepository.findAllByUserIdAndDeliveryDateGreaterThanEqualAndDeliveryDateLessThanOrderByDeliveryDateDescIdDesc(
                userId,
                requestedMonth.atDay(1),
                requestedMonth.plusMonths(1).atDay(1)
            ).stream().map(this::toCalendarItem).toList()
        );
    }

    public OrderHistoryResponse getOrderHistory(Long userId, String month, Integer page) {
        validateUserId(userId);
        YearMonth requestedMonth = requireMonth(month);
        int requestedPage = requirePage(page);
        Page<Order> orders = orderRepository.findByUserIdAndDeliveryDateGreaterThanEqualAndDeliveryDateLessThan(
            userId,
            requestedMonth.atDay(1),
            requestedMonth.plusMonths(1).atDay(1),
            PageRequest.of(
                requestedPage - 1,
                ORDER_HISTORY_PAGE_SIZE,
                Sort.by(Sort.Order.desc("deliveryDate"), Sort.Order.desc("id"))
            )
        );
        return new OrderHistoryResponse(
            requestedMonth.toString(),
            orders.getContent().stream().map(this::toListItem).toList(),
            requestedPage,
            ORDER_HISTORY_PAGE_SIZE,
            orders.getTotalElements(),
            orders.getTotalPages(),
            orders.hasPrevious(),
            orders.hasNext()
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
            menu.getImageUrl(),
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

    private OrderCalendarResponse.OrderItemResponse toCalendarItem(Order order) {
        return new OrderCalendarResponse.OrderItemResponse(
            order.getPublicId(),
            order.getDeliveryDate(),
            order.getStatus()
        );
    }

    private YearMonth requireMonth(String value) {
        if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("조회할 월은 YYYY-MM 형식이어야 합니다.");
        }
        return YearMonth.parse(value);
    }

    private int requirePage(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("페이지는 1 이상이어야 합니다.");
        }
        return value;
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
