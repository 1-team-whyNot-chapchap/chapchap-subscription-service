package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.exception.PaymentTransactionNotFoundException;
import com.chapchap.subscription.domain.payment.service.result.CompletedFirstPayment;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 실제 외부 응답을 결제 시도·거래·주문별 배분에 하나의 로컬 트랜잭션으로 확정한다. */
@Service
public class FirstPaymentCompletionService {
    private static final int FIRST_ATTEMPT_SEQUENCE = 1;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    /**
     * 거래·시도·배분 저장소를 사용해 첫 결제 결과 확정 Service를 구성한다.
     *
     * @param paymentTransactionRepository 결제 거래 저장소
     * @param paymentAttemptRepository 결제 처리 시도 저장소
     * @param paymentAllocationRepository 주문별 결제금액 배분 저장소
     */
    public FirstPaymentCompletionService(
        PaymentTransactionRepository paymentTransactionRepository,
        PaymentAttemptRepository paymentAttemptRepository,
        PaymentAllocationRepository paymentAllocationRepository
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    /**
     * 외부 결제의 명시적 성공·결제 거절·서버 연동 실패를 로컬 결제 데이터에 확정한다.
     *
     * <p>timeout처럼 결과를 받지 못한 경우에는 실행 결과 자체가 만들어지지 않으므로
     * 이 메서드를 호출하지 않는다. 그 경우 거래는 처리 중 상태로 남는다.</p>
     *
     * @param executionResult 실제 사용 결제수단과 외부 Provider의 명시적 응답
     * @param allocationCommands 성공 시 생성할 주문별 결제금액 배분. 실패일 때는 비어 있어야 함
     * @return 거래·시도·배분을 로컬 데이터에 확정한 결과
     */
    @Transactional
    public CompletedFirstPayment complete(
        FirstPaymentExecutionResult executionResult,
        List<PaymentAllocationCommand> allocationCommands
    ) {
        PaymentTransaction transaction = paymentTransactionRepository
            .findById(executionResult.paymentTransactionId())
            .orElseThrow(PaymentTransactionNotFoundException::new);

        validateExecutionMatchesTransaction(transaction, executionResult);
        validateAttemptIsNotRecorded(executionResult.idempotencyKey());

        AutomaticPaymentResult providerResult = executionResult.providerResult();
        if (providerResult.isPaid()) {
            List<PaymentAllocation> allocations = createValidatedAllocations(transaction, allocationCommands);
            PaymentAttempt attempt = createSuccessfulAttempt(executionResult);

            paymentAttemptRepository.save(attempt);
            paymentAllocationRepository.saveAll(allocations);
            transaction.markAsSucceeded();

            return new CompletedFirstPayment(
                transaction.getId(),
                transaction.getStatus(),
                PaymentAttemptResult.SUCCESS,
                allocations.size(),
                providerResult.status()
            );
        }

        requireNoAllocationsForFailure(allocationCommands);
        PaymentAttempt attempt = createFailedAttempt(executionResult);
        paymentAttemptRepository.save(attempt);
        transaction.markAsFailed();

        return new CompletedFirstPayment(
            transaction.getId(),
            transaction.getStatus(),
            PaymentAttemptResult.FAILURE,
            0,
            providerResult.status()
        );
    }

    private void validateExecutionMatchesTransaction(
        PaymentTransaction transaction,
        FirstPaymentExecutionResult executionResult
    ) {
        if (transaction.getStatus() != PaymentTransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only a processing first payment can be completed");
        }
        if (!transaction.getTransactionAmount().equals(executionResult.requestedAmount())) {
            throw new IllegalArgumentException("Executed amount does not match the payment transaction amount");
        }
        if (!executionResult.idempotencyKey().equals(transaction.getExternalRequestIdempotencyKey())) {
            throw new IllegalArgumentException("Executed idempotency key does not match the payment transaction");
        }
        if (!transaction.getPublicId().equals(executionResult.providerResult().externalPaymentId())) {
            throw new IllegalArgumentException("External payment id does not match the payment transaction");
        }
    }

    private void validateAttemptIsNotRecorded(String idempotencyKey) {
        if (paymentAttemptRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException("The external payment response is already recorded");
        }
    }

    private List<PaymentAllocation> createValidatedAllocations(
        PaymentTransaction transaction,
        List<PaymentAllocationCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("A successful first payment requires order allocations");
        }

        Set<Long> orderIds = new HashSet<>();
        long totalAllocatedAmount = 0L;
        for (PaymentAllocationCommand command : commands) {
            if (!orderIds.add(command.orderId())) {
                throw new IllegalArgumentException("An order must not be allocated more than once");
            }
            totalAllocatedAmount = Math.addExact(totalAllocatedAmount, command.allocationAmount());
        }
        if (totalAllocatedAmount != transaction.getTransactionAmount()) {
            throw new IllegalArgumentException("Allocated amount must equal the payment transaction amount");
        }

        PaymentAllocationType allocationType = transaction.getTransactionType()
            == com.chapchap.subscription.domain.payment.entity.PaymentTransactionType.SETTING_CHANGE_PAYMENT
            ? PaymentAllocationType.SETTING_CHANGE_PAYMENT
            : PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT;
        return commands.stream()
            .map(command -> PaymentAllocation.create(
                command.orderId(),
                transaction.getId(),
                allocationType,
                command.allocationAmount()
            ))
            .toList();
    }

    private void requireNoAllocationsForFailure(List<PaymentAllocationCommand> commands) {
        if (commands != null && !commands.isEmpty()) {
            throw new IllegalArgumentException("A failed first payment must not create allocations");
        }
    }

    private PaymentAttempt createSuccessfulAttempt(FirstPaymentExecutionResult executionResult) {
        AutomaticPaymentResult result = executionResult.providerResult();
        return PaymentAttempt.success(
            executionResult.paymentTransactionId(),
            executionResult.paymentMethodId(),
            executionResult.providerCode(),
            FIRST_ATTEMPT_SEQUENCE,
            executionResult.idempotencyKey(),
            executionResult.requestedAmount(),
            executionResult.requestedAt(),
            executionResult.respondedAt(),
            result.externalPaymentId(),
            result.externalTransactionRef(),
            result.externalResultCode()
        );
    }

    private PaymentAttempt createFailedAttempt(FirstPaymentExecutionResult executionResult) {
        AutomaticPaymentResult result = executionResult.providerResult();
        return PaymentAttempt.failure(
            executionResult.paymentTransactionId(),
            executionResult.paymentMethodId(),
            executionResult.providerCode(),
            FIRST_ATTEMPT_SEQUENCE,
            executionResult.idempotencyKey(),
            executionResult.requestedAmount(),
            executionResult.requestedAt(),
            executionResult.respondedAt(),
            result.externalPaymentId(),
            result.externalResultCode(),
            result.failureReason()
        );
    }
}
