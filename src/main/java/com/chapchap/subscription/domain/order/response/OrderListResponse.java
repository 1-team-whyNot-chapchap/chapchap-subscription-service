package com.chapchap.subscription.domain.order.response;

import com.chapchap.subscription.domain.order.entity.OrderStatus;

import java.time.LocalDate;
import java.util.List;

/** 인증 고객의 최소 주문 목록 응답이다. */
public record OrderListResponse(List<OrderItemResponse> orders) {
    public OrderListResponse {
        orders = List.copyOf(orders);
    }

    public record OrderItemResponse(
        String orderId,
        LocalDate deliveryDate,
        OrderStatus status,
        Long amount
    ) {
    }
}
