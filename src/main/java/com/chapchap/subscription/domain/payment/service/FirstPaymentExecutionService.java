package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentClient;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentRequest;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.security.BillingKeyProtector;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentExecutionCommand;
import com.chapchap.subscription.domain.payment.service.exception.CurrentPaymentMethodUnavailableException;
import com.chapchap.subscription.domain.payment.service.exception.PaymentTransactionNotFoundException;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.global.exception.payment.PaymentProviderUnavailableException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 현재 결제수단을 한 번 고정하고 DB 트랜잭션 밖에서 외부 첫 결제를 실행한다. */
@Service
public class FirstPaymentExecutionService {
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String CURRENCY_KRW = "KRW";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BillingKeyProtector billingKeyProtector;
    private final AutomaticPaymentClient automaticPaymentClient;

    /**
     * 처리 중 거래·현재 결제수단·보호값·외부 Client 의존성으로 실행 Service를 구성한다.
     *
     * @param paymentTransactionRepository 결제 거래 저장소
     * @param paymentMethodRepository 현재 결제수단 저장소
     * @param billingKeyProtector 보호된 결제수단 참조값 복호화 도구
     * @param automaticPaymentClient 실제 외부 자동결제 Client
     */
    public FirstPaymentExecutionService(
        PaymentTransactionRepository paymentTransactionRepository,
        PaymentMethodRepository paymentMethodRepository,
        BillingKeyProtector billingKeyProtector,
        AutomaticPaymentClient automaticPaymentClient
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.billingKeyProtector = billingKeyProtector;
        this.automaticPaymentClient = automaticPaymentClient;
    }

    /**
     * 처리 중 거래의 고객에게 현재 지정된 결제수단으로 외부 자동결제를 요청한다.
     *
     * <p>이 메서드에는 트랜잭션을 선언하지 않는다. 외부 Provider 응답을 기다리는 동안
     * DB 트랜잭션과 Lock을 유지하지 않기 위한 경계다.</p>
     *
     * @param command 실행할 결제 거래와 외부 표시 주문명
     * @return 실제 사용한 결제수단과 외부 응답을 포함한 결과 확정 입력
     */
    public FirstPaymentExecutionResult execute(FirstPaymentExecutionCommand command) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(command.paymentTransactionId())
            .orElseThrow(PaymentTransactionNotFoundException::new);
        validateExecutable(transaction);

        PaymentMethod paymentMethod = paymentMethodRepository
            .findByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
                transaction.getUserId(),
                PaymentMethodStatus.AVAILABLE
            )
            .orElseThrow(CurrentPaymentMethodUnavailableException::new);

        String externalMethodReference = billingKeyProtector.unprotect(
            transaction.getUserId(),
            paymentMethod.getProtectedExternalMethodRef()
        );
        AutomaticPaymentRequest request = new AutomaticPaymentRequest(
            transaction.getPublicId(),
            transaction.getExternalRequestIdempotencyKey(),
            externalMethodReference,
            command.orderName(),
            transaction.getTransactionAmount(),
            CURRENCY_KRW
        );

        LocalDateTime requestedAt = LocalDateTime.now(BUSINESS_ZONE_ID);
        AutomaticPaymentResult providerResult = requestPaymentOrFindConfirmedResult(request);
        LocalDateTime respondedAt = LocalDateTime.now(BUSINESS_ZONE_ID);

        return new FirstPaymentExecutionResult(
            transaction.getId(),
            paymentMethod.getId(),
            paymentMethod.getProviderCode(),
            transaction.getExternalRequestIdempotencyKey(),
            transaction.getTransactionAmount(),
            requestedAt,
            respondedAt,
            providerResult
        );
    }

    /**
     * 최초 POST의 결제 결과가 불명확할 때만 같은 paymentId를 한 번 조회한다.
     *
     * <p>조회 결과가 PAID 또는 FAILED로 확정되지 않으면 최초 예외를 다시 전파한다.
     * 따라서 상위 흐름은 기존 PAYMENT_003 처리로 결제·구독 관련 데이터를 처리 중 상태에 남기며,
     * 여기서 새 POST나 추가 조회를 수행하지 않는다.</p>
     */
    private AutomaticPaymentResult requestPaymentOrFindConfirmedResult(AutomaticPaymentRequest request) {
        try {
            return automaticPaymentClient.pay(request);
        } catch (PaymentProviderUnavailableException exception) {
            return automaticPaymentClient.findConfirmedResult(request.externalPaymentId())
                .orElseThrow(() -> exception);
        }
    }

    private void validateExecutable(PaymentTransaction transaction) {
        if (transaction.getStatus() != PaymentTransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only a processing first payment can be executed");
        }
        if (transaction.getExternalRequestIdempotencyKey() == null
            || transaction.getExternalRequestIdempotencyKey().isBlank()) {
            throw new IllegalStateException("A processing first payment must have an idempotency key");
        }
    }
}
