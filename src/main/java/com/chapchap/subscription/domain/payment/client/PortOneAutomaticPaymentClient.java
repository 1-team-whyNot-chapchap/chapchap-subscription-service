package com.chapchap.subscription.domain.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chapchap.subscription.global.exception.payment.PaymentProviderUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** PortOne V2 빌링키 결제 API를 Provider 중립 자동결제 계약에 연결한다. */
@Component
public class PortOneAutomaticPaymentClient implements AutomaticPaymentClient {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PAID_STATUS = "PAID";
    private static final String FAILED_STATUS = "FAILED";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final String GENERIC_DECLINE_REASON = "외부 결제 승인이 거절되었습니다.";
    private static final String GENERIC_CONFIGURATION_FAILURE_REASON = "외부 결제 연동 설정 오류가 발생했습니다.";
    private static final String BILLING_KEY_NOT_FOUND = "BILLINGKEYNOTFOUND";
    private static final String BILLING_KEY_ALREADY_DELETED = "BILLINGKEYALREADYDELETED";
    private static final String INVALID_REQUEST = "INVALIDREQUEST";
    private static final String CHANNEL_NOT_FOUND = "CHANNELNOTFOUND";
    private static final String UNAUTHORIZED = "UNAUTHORIZED";
    private static final String FORBIDDEN = "FORBIDDEN";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // ========= [TODO: SUB-FN-004 / PG별 결제 요청 계약] =========
    // 이유: 현재 Source에는 실제 사용할 PG·채널과 필수 고객정보 계약이 확정되어 있지 않다.
    // 완료 조건: 팀이 실제 PG·채널과 customer 필드별 값의 출처·외부 식별자 정책을 확정한다.
    // 후속 작업: 필요한 고객정보만 AutomaticPaymentRequest와 PortOne 요청 DTO에 추가하고 Mock·실결제로 검증한다.
    // 검토 사항: 내부 사용자 PK를 근거 없이 외부 customer.id로 전송하거나 개인정보를 일반 로그에 남기지 않는다.
    // ========= [/TODO] =============================================

    // ========= [TODO: SUB-FN-004 / PgProviderError 세부 분류] =========
    // 이유: 실제 사용할 PG사와 명시적 결제 거절로 인정할 pgCode 목록이 아직 확정되지 않았다.
    // 완료 조건: 실제 PG·테스트 채널에서 거절 응답을 수집하고 팀이 허용 목록을 확정한다.
    // 후속 작업: 확정된 pgCode만 PAYMENT_008 결과로 변환하고 나머지는 PAYMENT_003으로 유지한다.
    // 검토 사항: PgProviderError라는 type만으로 승인 실패를 확정하거나 PG 원문을 고객 응답에 노출하지 않는다.
    // ========= [/TODO] =================================================

    /** PortOne V2 주소·Secret과 공식 권장 timeout으로 실제 HTTP Client를 구성한다. */
    @Autowired
    public PortOneAutomaticPaymentClient(
        RestClient.Builder restClientBuilder,
        @Value("${portone.api.base-url}") String baseUrl,
        @Value("${portone.api.secret}") String apiSecret
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
            .requestFactory(requestFactory)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /** 테스트에서 외부 네트워크 없이 요청·응답 계약을 검증하기 위한 생성자다. */
    PortOneAutomaticPaymentClient(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    /** 빌링키 결제를 요청하고 PortOne의 확정 상태를 내부 성공·업무 실패·설정 실패로 변환한다. */
    @Override
    public AutomaticPaymentResult pay(AutomaticPaymentRequest request) {
        try {
            PortOneBillingKeyPaymentResponse response = restClient.post()
                .uri("/payments/{paymentId}/billing-key", request.externalPaymentId())
                .header(IDEMPOTENCY_KEY_HEADER, quoteStructuredField(request.idempotencyKey()))
                .body(PortOneBillingKeyPaymentRequest.from(request))
                .retrieve()
                .body(PortOneBillingKeyPaymentResponse.class);

            return toAutomaticPaymentResult(request, response);
        } catch (RestClientResponseException exception) {
            return mapProviderError(request, exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new PaymentProviderUnavailableException();
        }
    }

    /**
     * 최초 결제 요청의 응답이 불명확할 때 같은 paymentId의 최종 상태만 조회한다.
     *
     * <p>이 조회는 결제를 새로 요청하지 않으며, 응답 오류·미확정 상태·식별자 불일치는
     * 호출자가 기존 PAYMENT_003 처리 경로를 유지할 수 있도록 빈 결과로 변환한다.</p>
     */
    @Override
    public Optional<AutomaticPaymentResult> findConfirmedResult(String externalPaymentId) {
        try {
            PortOneBillingKeyPaymentResponse.Payment payment = restClient.get()
                .uri("/payments/{paymentId}", externalPaymentId)
                .retrieve()
                .body(PortOneBillingKeyPaymentResponse.Payment.class);

            return toConfirmedPaymentResult(externalPaymentId, payment);
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private AutomaticPaymentResult toAutomaticPaymentResult(
        AutomaticPaymentRequest request,
        PortOneBillingKeyPaymentResponse response
    ) {
        return toConfirmedPaymentResult(request.externalPaymentId(), response == null ? null : response.payment())
            .orElseThrow(PaymentProviderUnavailableException::new);
    }

    private Optional<AutomaticPaymentResult> toConfirmedPaymentResult(
        String externalPaymentId,
        PortOneBillingKeyPaymentResponse.Payment payment
    ) {
        if (payment == null || !externalPaymentId.equals(payment.id())) {
            return Optional.empty();
        }
        if (PAID_STATUS.equals(payment.status())) {
            if (payment.transactionId() == null || payment.transactionId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(AutomaticPaymentResult.success(
                payment.id(),
                payment.transactionId(),
                PAID_STATUS
            ));
        }
        if (FAILED_STATUS.equals(payment.status())) {
            return Optional.of(AutomaticPaymentResult.declined(
                payment.id(),
                failureCode(payment.failure()),
                GENERIC_DECLINE_REASON
            ));
        }
        return Optional.empty();
    }

    private AutomaticPaymentResult mapProviderError(
        AutomaticPaymentRequest request,
        RestClientResponseException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)
            || status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return providerConfigurationFailure(request, "HTTP_" + status.value());
        }

        String errorType = parseErrorType(exception);
        String normalizedErrorType = normalizeErrorType(errorType);
        if (BILLING_KEY_NOT_FOUND.equals(normalizedErrorType)
            || BILLING_KEY_ALREADY_DELETED.equals(normalizedErrorType)) {
            return AutomaticPaymentResult.declined(
                request.externalPaymentId(),
                errorType,
                GENERIC_DECLINE_REASON
            );
        }
        if (INVALID_REQUEST.equals(normalizedErrorType)
            || CHANNEL_NOT_FOUND.equals(normalizedErrorType)
            || UNAUTHORIZED.equals(normalizedErrorType)
            || FORBIDDEN.equals(normalizedErrorType)) {
            return providerConfigurationFailure(request, errorType);
        }
        throw new PaymentProviderUnavailableException();
    }

    private AutomaticPaymentResult providerConfigurationFailure(
        AutomaticPaymentRequest request,
        String externalResultCode
    ) {
        return AutomaticPaymentResult.providerConfigurationFailed(
            request.externalPaymentId(),
            externalResultCode,
            GENERIC_CONFIGURATION_FAILURE_REASON
        );
    }

    private String parseErrorType(RestClientResponseException exception) {
        try {
            String errorType = objectMapper
                .readTree(exception.getResponseBodyAsByteArray())
                .path("type")
                .asText(null);
            if (errorType == null || errorType.isBlank()) {
                return null;
            }
            return errorType.trim();
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeErrorType(String errorType) {
        if (errorType == null) {
            return null;
        }
        String normalized = errorType
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        return normalized.endsWith("ERROR")
            ? normalized.substring(0, normalized.length() - "ERROR".length())
            : normalized;
    }

    private String failureCode(PortOneBillingKeyPaymentResponse.Failure failure) {
        if (failure == null || failure.pgCode() == null || failure.pgCode().isBlank()) {
            return FAILED_STATUS;
        }
        return failure.pgCode();
    }

    private String quoteStructuredField(String value) {
        return '"' + value + '"';
    }
}
