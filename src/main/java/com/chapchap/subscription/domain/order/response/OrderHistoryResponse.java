package com.chapchap.subscription.domain.order.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 선택 배송월의 주문 목록 페이지와 페이지 이동 정보를 반환한다. */
@Schema(description = "월별 주문 목록 페이지")
public record OrderHistoryResponse(
    @Schema(description = "조회한 배송월", example = "2026-09")
    String month,
    @Schema(description = "현재 페이지 주문 목록")
    List<OrderListResponse.OrderItemResponse> orders,
    @Schema(description = "1부터 시작하는 요청 페이지", example = "1")
    int page,
    @Schema(description = "고정 페이지 크기", example = "3")
    int size,
    @Schema(description = "해당 월 전체 주문 수", example = "8")
    long totalElements,
    @Schema(description = "해당 월 전체 페이지 수", example = "3")
    int totalPages,
    boolean hasPrevious,
    boolean hasNext
) {
    public OrderHistoryResponse {
        orders = List.copyOf(orders);
    }
}
