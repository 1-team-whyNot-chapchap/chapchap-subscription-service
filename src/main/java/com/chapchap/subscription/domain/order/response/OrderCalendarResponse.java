package com.chapchap.subscription.domain.order.response;

import com.chapchap.subscription.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 선택 배송월의 주문을 달력 표시용 최소 정보로 반환한다. */
@Schema(description = "월별 주문 달력")
public record OrderCalendarResponse(
    @Schema(description = "조회한 배송월", example = "2026-09")
    String month,
    @Schema(description = "해당 월 주문")
    List<OrderItemResponse> orders
) {
    public OrderCalendarResponse {
        orders = List.copyOf(orders);
    }

    @Schema(description = "월별 주문 달력 항목")
    public record OrderItemResponse(
        @Schema(description = "주문의 공개 UUID", format = "uuid")
        String orderId,
        @Schema(description = "배송 예정일", example = "2026-09-14")
        LocalDate deliveryDate,
        @Schema(description = "주문 상태")
        OrderStatus status
    ) {
    }
}
