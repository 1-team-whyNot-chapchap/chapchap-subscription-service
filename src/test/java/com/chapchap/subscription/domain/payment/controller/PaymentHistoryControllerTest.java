package com.chapchap.subscription.domain.payment.controller;

import com.chapchap.subscription.domain.payment.response.PaymentDetailResponse;
import com.chapchap.subscription.domain.payment.response.PaymentListResponse;
import com.chapchap.subscription.domain.payment.response.RefundDetailResponse;
import com.chapchap.subscription.domain.payment.response.RefundListResponse;
import com.chapchap.subscription.domain.payment.service.PaymentHistoryQueryService;
import com.chapchap.subscription.global.response.GlobalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentHistoryControllerTest {
    private static final Long USER_ID = 10L;

    @Test
    void 인증_고객의_결제_목록과_상세를_조회한다() {
        PaymentHistoryQueryService service = mock(PaymentHistoryQueryService.class);
        Authentication authentication = authentication();
        PaymentListResponse list = new PaymentListResponse(List.of());
        when(service.getPayments(USER_ID)).thenReturn(list);
        when(service.getPayment(USER_ID, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(null);
        PaymentHistoryController controller = new PaymentHistoryController(service);

        GlobalResponse<PaymentListResponse> listResponse = controller.getPayments(authentication);
        GlobalResponse<PaymentDetailResponse> detailResponse = controller.getPayment(
            authentication, "550e8400-e29b-41d4-a716-446655440000"
        );

        assertThat(listResponse.data()).isSameAs(list);
        assertThat(detailResponse.code()).isEqualTo("00");
        verify(service).getPayments(USER_ID);
        verify(service).getPayment(USER_ID, "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void 인증_고객의_환불_목록과_상세를_조회한다() {
        PaymentHistoryQueryService service = mock(PaymentHistoryQueryService.class);
        Authentication authentication = authentication();
        RefundListResponse list = new RefundListResponse(List.of());
        when(service.getRefunds(USER_ID)).thenReturn(list);
        when(service.getRefund(USER_ID, "650e8400-e29b-41d4-a716-446655440000")).thenReturn(null);
        PaymentHistoryController controller = new PaymentHistoryController(service);

        GlobalResponse<RefundListResponse> listResponse = controller.getRefunds(authentication);
        GlobalResponse<RefundDetailResponse> detailResponse = controller.getRefund(
            authentication, "650e8400-e29b-41d4-a716-446655440000"
        );

        assertThat(listResponse.data()).isSameAs(list);
        assertThat(detailResponse.code()).isEqualTo("00");
        verify(service).getRefunds(USER_ID);
        verify(service).getRefund(USER_ID, "650e8400-e29b-41d4-a716-446655440000");
    }

    private Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USER_ID.toString());
        return authentication;
    }
}
