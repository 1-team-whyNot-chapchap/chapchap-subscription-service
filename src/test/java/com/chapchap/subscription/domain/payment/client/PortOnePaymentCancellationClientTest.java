package com.chapchap.subscription.domain.payment.client;

import com.chapchap.subscription.global.exception.payment.PaymentProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOnePaymentCancellationClientTest {
    private static final String BASE_URL = "https://api.portone.test";
    private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private MockRestServiceServer server;
    private PortOnePaymentCancellationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne test-secret");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentCancellationClient(builder.build());
    }

    @Test
    void 원결제_부분취소_요청과_멱등성키를_전송한다() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "\"cancel-key-1234567890\""))
            .andExpect(content().json("""
                {"amount":3000,"currentCancellableAmount":10000,
                 "reason":"구독 시작 전 고객 취소","requester":"CUSTOMER"}
                """))
            .andRespond(withSuccess("""
                {"cancellation":{"status":"SUCCEEDED","id":"cancel-1","pgCancellationId":"pg-1"}}
                """, MediaType.APPLICATION_JSON));

        PaymentCancellationResult result = client.cancel(new PaymentCancellationRequest(
            PAYMENT_ID, "cancel-key-1234567890", 3000, 10000, "구독 시작 전 고객 취소"
        ));

        assertThat(result.status()).isEqualTo(PaymentCancellationStatus.SUCCEEDED);
        assertThat(result.externalCancellationId()).isEqualTo("cancel-1");
        server.verify();
    }

    @Test
    void 명시적인_4xx는_취소실패로_변환한다() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\":\"CancelAmountExceedsCancellableAmountError\",\"message\":\"detail\"}"));

        PaymentCancellationResult result = client.cancel(new PaymentCancellationRequest(
            PAYMENT_ID, "cancel-key-1234567890", 3000, 10000, "고객 요청"
        ));

        assertThat(result.status()).isEqualTo(PaymentCancellationStatus.DECLINED);
        assertThat(result.failureReason()).doesNotContain("detail");
    }

    @Test
    void 인증실패는_Provider설정실패로_변환한다() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        PaymentCancellationResult result = client.cancel(new PaymentCancellationRequest(
            PAYMENT_ID, "cancel-key-1234567890", 3000, 10000, "고객 요청"
        ));

        assertThat(result.status()).isEqualTo(PaymentCancellationStatus.PROVIDER_CONFIGURATION_FAILED);
    }

    @Test
    void 미확정_응답은_처리중을_유지하도록_예외를_발생시킨다() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.cancel(new PaymentCancellationRequest(
            PAYMENT_ID, "cancel-key-1234567890", 3000, 10000, "고객 요청"
        ))).isInstanceOf(PaymentProviderUnavailableException.class);
    }
}
