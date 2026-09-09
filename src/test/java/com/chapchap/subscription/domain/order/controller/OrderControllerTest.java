package com.chapchap.subscription.domain.order.controller;

import com.chapchap.subscription.domain.order.response.OrderDetailResponse;
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

    private Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("10");
        return authentication;
    }
}
