package com.chapchap.subscription.domain.order.response;

import com.chapchap.subscription.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 인증 고객의 최소 주문 목록 응답이다. */
@Schema(description = "인증 고객의 주문 목록")
public record OrderListResponse(
    @Schema(description = "주문 목록")
    List<OrderItemResponse> orders
) {
    public OrderListResponse {
        orders = List.copyOf(orders);
    }

    @Schema(description = "주문 목록 항목")
    public record OrderItemResponse(
        @Schema(description = "주문의 공개 UUID", format = "uuid", example = "7834a801-703d-4d3b-b08e-109a898d999b")
        String orderId,
        @Schema(description = "배송 예정일", example = "2026-09-14")
        LocalDate deliveryDate,
        @Schema(description = "주문 상태")
        OrderStatus status,
        @Schema(description = "주문 금액(원)", example = "32700")
        Long amount
    ) {
    }
}
