package com.chapchap.subscription.domain.order.controller;

import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
import com.chapchap.subscription.domain.order.response.OrderCalendarResponse;
import com.chapchap.subscription.domain.order.response.OrderHistoryResponse;
import com.chapchap.subscription.domain.order.response.OrderListResponse;
import com.chapchap.subscription.domain.order.service.OrderQueryService;
import com.chapchap.subscription.global.response.GlobalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {
    @Test
    void 인증_사용자의_주문_목록을_조회한다() {
        OrderQueryService service = mock(OrderQueryService.class);
        Authentication authentication = authentication();
        OrderListResponse expected = new OrderListResponse(List.of());
        when(service.getOrders(10L)).thenReturn(expected);

        GlobalResponse<OrderListResponse> response =
            new OrderController(service).getOrders(authentication);

        assertThat(response.data()).isSameAs(expected);
        verify(service).getOrders(10L);
    }

    @Test
    void 인증_사용자와_공개_식별자로_주문_상세를_조회한다() {
        OrderQueryService service = mock(OrderQueryService.class);
        Authentication authentication = authentication();
        when(service.getOrder(10L, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(null);

        GlobalResponse<OrderDetailResponse> response =
            new OrderController(service).getOrder(authentication, "550e8400-e29b-41d4-a716-446655440000");

        assertThat(response.code()).isEqualTo("00");
        verify(service).getOrder(10L, "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void 인증_사용자의_월별_달력_주문을_조회한다() {
        OrderQueryService service = mock(OrderQueryService.class);
        Authentication authentication = authentication();
        OrderCalendarResponse expected = new OrderCalendarResponse("2026-09", List.of());
        when(service.getOrderCalendar(10L, "2026-09")).thenReturn(expected);

        GlobalResponse<OrderCalendarResponse> response =
            new OrderController(service).getOrderCalendar(authentication, "2026-09");

        assertThat(response.data()).isSameAs(expected);
        verify(service).getOrderCalendar(10L, "2026-09");
    }

    @Test
    void 인증_사용자의_월별_주문_페이지를_조회한다() {
        OrderQueryService service = mock(OrderQueryService.class);
        Authentication authentication = authentication();
        OrderHistoryResponse expected = new OrderHistoryResponse(
            "2026-09", List.of(), 2, 3, 4, 2, true, false
        );
        when(service.getOrderHistory(10L, "2026-09", 2)).thenReturn(expected);

        GlobalResponse<OrderHistoryResponse> response =
            new OrderController(service).getOrderHistory(authentication, "2026-09", 2);

        assertThat(response.data()).isSameAs(expected);
        verify(service).getOrderHistory(10L, "2026-09", 2);
    }

    private Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("10");
        return authentication;
    }
}
