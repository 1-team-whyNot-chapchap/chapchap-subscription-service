package com.chapchap.subscription.domain.payment.client;

import com.chapchap.subscription.global.exception.payment.PaymentMethodInvalidException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderAuthenticationFailedException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PortOnePaymentMethodClient {
    private static final String ISSUED_STATUS = "ISSUED";
    private static final String CARD_METHOD_TYPE = "BillingKeyPaymentMethodCard";
    private final RestClient restClient; // PortOne에 HTTP 요청을 보내는 도구

    // PortOne 전용 HTTP Client
    public PortOnePaymentMethodClient(
        RestClient.Builder restClientBuilder
        , @Value("${portone.api.base-url}") String baseUrl
        , @Value("${portone.api.secret}") String apiSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                .build();
    }

    // PortOne에 billingKey 조회 요청
    public PaymentMethodVerificationResult verifyBillingKey(String billingKey) {
        try {
            PortOneBillingKeyResponse response = restClient.get()
                    .uri("/billing-keys/{billingKey}", billingKey)
                    .retrieve()
                    .body(PortOneBillingKeyResponse.class);

            if (response == null) {
                throw new PaymentProviderUnavailableException();
            }
            return toVerificationResult(response);

        } catch (RestClientResponseException e) {
            throw mapProviderError(e);
        } catch (ResourceAccessException e) {
            throw new PaymentProviderUnavailableException();
        }
    }

    private RuntimeException mapProviderError(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();

        if (status.isSameCodeAs(HttpStatus.BAD_REQUEST) || status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new PaymentMethodInvalidException();
        }

        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return new PaymentProviderAuthenticationFailedException();
        }

        if (status.is5xxServerError()) {
            return new PaymentProviderUnavailableException();
        }
        return new IllegalStateException("예상하지 못한 결제 서비스 응답 상태입니다: " + status.value());
    }

    private PaymentMethodVerificationResult toVerificationResult(PortOneBillingKeyResponse response) {
        PortOneBillingKeyResponse.Card card = findCard(response);

        return new PaymentMethodVerificationResult(
                ISSUED_STATUS.equals(response.status())
                , card != null ? card.name() : null
                , card != null ? card.number() : null
        );
    }

    private PortOneBillingKeyResponse.Card findCard(PortOneBillingKeyResponse response) {
        if (response.methods() == null) {
            return null;
        }

        return response.methods().stream()
                .filter(method ->
                    method != null
                        && CARD_METHOD_TYPE.equals(method.type())
                )
                .map(PortOneBillingKeyResponse.Method::card)
                .filter(card -> card != null)
                .findFirst()
                .orElse(null);
    }
}
