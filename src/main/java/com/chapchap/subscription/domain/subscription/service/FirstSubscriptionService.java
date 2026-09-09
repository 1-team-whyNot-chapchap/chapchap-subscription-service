package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.service.FirstPaymentExecutionService;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentExecutionCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionResponse;
import com.chapchap.subscription.global.exception.payment.PaymentDeclinedException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderAuthenticationFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** prepare, 트랜잭션 밖 PG 호출, 결과 확정을 순서대로 연결하는 첫 구독 오케스트레이터다. */
@Service
public class FirstSubscriptionService {
    private final FirstSubscriptionPreparationService preparationService;
    private final FirstPaymentExecutionService paymentExecutionService;
    private final FirstSubscriptionCompletionService completionService;

    /** 분리된 prepare·외부 결제·complete 경계를 조합한다. */
    public FirstSubscriptionService(
        FirstSubscriptionPreparationService preparationService,
        FirstPaymentExecutionService paymentExecutionService,
        FirstSubscriptionCompletionService completionService
    ) {
        this.preparationService = preparationService;
        this.paymentExecutionService = paymentExecutionService;
        this.completionService = completionService;
    }

    /**
     * 첫 구독을 신청하고 결제를 실행한다. 기존 PROCESSING 요청에는 외부 결제를 다시 요청하지 않는다.
     */
    public FirstSubscriptionResponse subscribe(Long userId, FirstSubscriptionRequest request) {
        PreparedFirstSubscription prepared;
        try {
            prepared = preparationService.prepare(userId, request);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
            Optional<PreparedFirstSubscription> concurrent =
                preparationService.recoverConcurrentProcessing(userId);
            if (concurrent.isPresent()) {
                return toResponse(concurrent.get());
            }
            throw conflict;
        }
        if (!prepared.paymentRequired()) {
            return toResponse(prepared);
        }

        FirstPaymentExecutionResult executionResult = paymentExecutionService.execute(
            new FirstPaymentExecutionCommand(prepared.paymentTransactionId(), "첫 구독 결제")
        );
        AutomaticPaymentStatus status = completionService.complete(prepared, executionResult);
        if (status == AutomaticPaymentStatus.DECLINED) {
            throw new PaymentDeclinedException();
        }
        if (status == AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED) {
            throw new PaymentProviderAuthenticationFailedException();
        }
        return new FirstSubscriptionResponse(
            prepared.subscriptionPublicId(),
            com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus.SCHEDULED,
            prepared.periodStartDate(),
            prepared.periodEndDate()
        );
    }

    private FirstSubscriptionResponse toResponse(PreparedFirstSubscription prepared) {
        return new FirstSubscriptionResponse(
            prepared.subscriptionPublicId(),
            prepared.status(),
            prepared.periodStartDate(),
            prepared.periodEndDate()
        );
    }
}
