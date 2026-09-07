package com.chapchap.subscription.domain.currentstate.controller;

import com.chapchap.subscription.domain.currentstate.response.CurrentPaymentStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentRefundStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentSubscriptionStateResponse;
import com.chapchap.subscription.domain.currentstate.service.CustomerAiCurrentStateQueryService;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.global.exception.GlobalExceptionHandler;
import com.chapchap.subscription.global.security.filter.HeaderAuthenticationFilter;
import com.chapchap.subscription.global.security.filter.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerAiCurrentStateController.class)
@Import({SecurityConfiguration.class, HeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class CustomerAiCurrentStateControllerTest {

    private static final String BASE_PATH = "/api/subscription/internal/v1/current-state";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerAiCurrentStateQueryService queryService;

    @Test
    void X_User_Id만으로_현재_결제를_최소_필드와_KST_Offset으로_반환한다() throws Exception {
        when(queryService.getCurrentPayment(10L)).thenReturn(new CurrentPaymentStateResponse(
            PaymentTransactionStatus.RETRY_WAITING,
            PaymentTransactionType.REGULAR_PAYMENT,
            12_900L,
            OffsetDateTime.parse("2026-09-07T09:00:00+09:00")
        ));

        mockMvc.perform(get(BASE_PATH + "/payment").header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("00"))
            .andExpect(jsonPath("$.message").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("RETRY_WAITING"))
            .andExpect(jsonPath("$.data.paymentType").value("REGULAR_PAYMENT"))
            .andExpect(jsonPath("$.data.amount").value(12_900))
            .andExpect(jsonPath("$.data.occurredAt").value("2026-09-07T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.paymentId").doesNotExist());
    }

    @Test
    void 최근_환불은_진행_금액과_nullable_완료시각만_반환한다() throws Exception {
        when(queryService.getCurrentRefund(10L)).thenReturn(new CurrentRefundStateResponse(
            RefundStatus.REVIEW_REQUIRED,
            RefundType.DELIVERY_PARTIAL_CANCELLATION,
            20_000L,
            12_000L,
            8_000L,
            OffsetDateTime.parse("2026-09-07T13:55:00+09:00"),
            null
        ));

        mockMvc.perform(get(BASE_PATH + "/refund").header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.data.refundType").value("DELIVERY_PARTIAL_CANCELLATION"))
            .andExpect(jsonPath("$.data.requestedAmount").value(20_000))
            .andExpect(jsonPath("$.data.refundedAmount").value(12_000))
            .andExpect(jsonPath("$.data.unprocessedAmount").value(8_000))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-09-07T13:55:00+09:00"))
            .andExpect(jsonPath("$.data.completedAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.failureReason").doesNotExist())
            .andExpect(jsonPath("$.data.refundId").doesNotExist());
    }

    @Test
    void 현재_구독은_실제_상태만_반환한다() throws Exception {
        when(queryService.getCurrentSubscription(10L))
            .thenReturn(new CurrentSubscriptionStateResponse(SubscriptionStatus.CANCELLATION_SCHEDULED));

        mockMvc.perform(get(BASE_PATH + "/subscription").header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLATION_SCHEDULED"))
            .andExpect(jsonPath("$.data.subscriptionId").doesNotExist())
            .andExpect(jsonPath("$.data.plan").doesNotExist());
    }

    @Test
    void 업무_데이터가_없으면_성공_응답의_data가_null이다() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/payment").header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("00"))
            .andExpect(jsonPath("$.message").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 잘못된_X_User_Id는_COMMON_001로_거절하고_Service를_호출하지_않는다() throws Exception {
        assertInvalidUserId(null);
        assertInvalidUserId("");
        assertInvalidUserId("   ");
        assertInvalidUserId("0");
        assertInvalidUserId("-1");
        assertInvalidUserId("1.0");
        assertInvalidUserId("user-1");
        assertInvalidUserId("9223372036854775808");

        verifyNoInteractions(queryService);
    }

    private void assertInvalidUserId(String rawUserId) throws Exception {
        var request = get(BASE_PATH + "/payment");
        if (rawUserId != null) {
            request.header("X-User-Id", rawUserId);
        }
        mockMvc.perform(request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_001"))
            .andExpect(jsonPath("$.message").value("요청이 유효하지 않습니다."))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
