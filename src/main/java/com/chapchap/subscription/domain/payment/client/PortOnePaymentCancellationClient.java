package com.chapchap.subscription.domain.payment.client;

import com.chapchap.subscription.global.exception.payment.PaymentProviderUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/** PortOne V2 결제 취소 API를 Provider 중립 경계에 연결한다. */
@Component
public class PortOnePaymentCancellationClient implements PaymentCancellationClient {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PortOnePaymentCancellationClient(RestClient.Builder builder,
        @Value("${portone.api.base-url}") String baseUrl,
        @Value("${portone.api.secret}") String apiSecret) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = builder.baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
            .requestFactory(factory)
            .build();
    }

    PortOnePaymentCancellationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PaymentCancellationResult cancel(PaymentCancellationRequest request) {
        try {
            PortOneCancelPaymentResponse response = restClient.post()
                .uri("/payments/{paymentId}/cancel", request.externalPaymentId())
                .header(IDEMPOTENCY_KEY_HEADER, '"' + request.idempotencyKey() + '"')
                .body(PortOneCancelPaymentRequest.from(request))
                .retrieve()
                .body(PortOneCancelPaymentResponse.class);
            PortOneCancelPaymentResponse.Cancellation cancellation = response == null ? null : response.cancellation();
            if (cancellation == null || !"SUCCEEDED".equals(cancellation.status())
                || cancellation.id() == null || cancellation.id().isBlank()) {
                throw new PaymentProviderUnavailableException();
            }
            return PaymentCancellationResult.succeeded(request.externalPaymentId(), cancellation.id());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                || exception.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                return PaymentCancellationResult.configurationFailed(request.externalPaymentId(), errorType(exception));
            }
            if (exception.getStatusCode().is4xxClientError()
                && !exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                return PaymentCancellationResult.declined(request.externalPaymentId(), errorType(exception));
            }
            throw new PaymentProviderUnavailableException();
        } catch (ResourceAccessException exception) {
            throw new PaymentProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new PaymentProviderUnavailableException();
        }
    }

    private String errorType(RestClientResponseException exception) {
        try {
            String type = objectMapper.readTree(exception.getResponseBodyAsByteArray()).path("type").asText(null);
            return type == null || type.isBlank() ? "HTTP_" + exception.getStatusCode().value() : type;
        } catch (IOException | IllegalArgumentException ignored) {
            return "HTTP_" + exception.getStatusCode().value();
        }
    }
}
