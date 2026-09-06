package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.exception.PaymentTransactionNotFoundException;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.global.kafka.customer.CustomerPaymentEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 정기결제 응답과 다음 기간·주문 상태를 하나의 로컬 트랜잭션으로 확정한다. */
@Service
public class RegularPaymentCompletionService {
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final OrderRepository orderRepository;
    private final CustomerPaymentEventPublisher customerPaymentPublisher;

    public RegularPaymentCompletionService(
        PaymentTransactionRepository transactionRepository,
        PaymentAttemptRepository attemptRepository,
        PaymentAllocationRepository allocationRepository,
        SubscriptionPeriodRepository periodRepository,
        OrderRepository orderRepository,
        CustomerPaymentEventPublisher customerPaymentPublisher
    ) {
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
        this.allocationRepository = allocationRepository;
        this.periodRepository = periodRepository;
        this.orderRepository = orderRepository;
        this.customerPaymentPublisher = customerPaymentPublisher;
    }

    /** 오전 첫 시도 또는 오후 마지막 시도의 명시적 응답을 확정한다. */
    @Transactional
    public AutomaticPaymentStatus complete(
        FirstPaymentExecutionResult execution,
        List<PaymentAllocationCommand> allocationCommands,
        boolean finalAttempt
    ) {
        PaymentTransaction transaction = transactionRepository.findWithLockById(execution.paymentTransactionId())
            .orElseThrow(PaymentTransactionNotFoundException::new);
        validateExecution(transaction, execution);

        int attemptSequence = attemptRepository
            .findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(transaction.getId()).size() + 1;
        int expectedSequence = finalAttempt ? 2 : 1;
        if (attemptSequence != expectedSequence) {
            throw new IllegalStateException("Unexpected regular payment attempt sequence");
        }
        if (attemptRepository.existsByIdempotencyKey(execution.idempotencyKey())) {
            throw new IllegalStateException("The external payment response is already recorded");
        }

        AutomaticPaymentResult provider = execution.providerResult();
        List<PaymentAllocation> allocations;
        if (provider.isPaid()) {
            allocations = createAllocations(transaction, allocationCommands);
        } else {
            requireNoAllocations(allocationCommands);
            allocations = List.of();
        }
        attemptRepository.save(toAttempt(execution, attemptSequence));
        if (provider.isPaid()) {
            allocationRepository.saveAll(allocations);
            transaction.markAsSucceeded();
            activatePeriodAndOrders(transaction.getSubscriptionPeriodId());
        } else if (finalAttempt) {
            transaction.markAsFailed();
            failPeriodAndOrders(transaction.getSubscriptionPeriodId());
        } else {
            transaction.waitForRegularPaymentRetry();
        }
        if (provider.isPaid()) {
            customerPaymentPublisher.publishCompletedAfterCommit(transaction.getId(), execution.respondedAt());
        } else {
            customerPaymentPublisher.publishRegularFailureAfterCommit(
                transaction.getId(), execution.respondedAt(), finalAttempt
            );
        }
        return provider.status();
    }

    private void validateExecution(PaymentTransaction transaction, FirstPaymentExecutionResult execution) {
        if (transaction.getTransactionType() != PaymentTransactionType.REGULAR_PAYMENT
            || transaction.getStatus() != PaymentTransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only a processing regular payment can be completed");
        }
        if (!transaction.getTransactionAmount().equals(execution.requestedAmount())
            || !transaction.getExternalRequestIdempotencyKey().equals(execution.idempotencyKey())
            || !transaction.getPublicId().equals(execution.providerResult().externalPaymentId())) {
            throw new IllegalArgumentException("Executed regular payment does not match the transaction");
        }
    }

    private PaymentAttempt toAttempt(FirstPaymentExecutionResult execution, int sequence) {
        AutomaticPaymentResult provider = execution.providerResult();
        if (provider.isPaid()) {
            return PaymentAttempt.success(
                execution.paymentTransactionId(), execution.paymentMethodId(), execution.providerCode(), sequence,
                execution.idempotencyKey(), execution.requestedAmount(), execution.requestedAt(),
                execution.respondedAt(), provider.externalPaymentId(), provider.externalTransactionRef(),
                provider.externalResultCode()
            );
        }
        return PaymentAttempt.failure(
            execution.paymentTransactionId(), execution.paymentMethodId(), execution.providerCode(), sequence,
            execution.idempotencyKey(), execution.requestedAmount(), execution.requestedAt(),
            execution.respondedAt(), provider.externalPaymentId(), provider.externalResultCode(),
            provider.failureReason()
        );
    }

    private List<PaymentAllocation> createAllocations(
        PaymentTransaction transaction,
        List<PaymentAllocationCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("A successful regular payment requires order allocations");
        }
        Set<Long> orderIds = new HashSet<>();
        long total = 0L;
        for (PaymentAllocationCommand command : commands) {
            if (!orderIds.add(command.orderId())) {
                throw new IllegalArgumentException("An order must not be allocated more than once");
            }
            total = Math.addExact(total, command.allocationAmount());
        }
        if (total != transaction.getTransactionAmount()) {
            throw new IllegalArgumentException("Allocated amount must equal the regular payment amount");
        }
        return commands.stream().map(command -> PaymentAllocation.create(
            command.orderId(), transaction.getId(), PaymentAllocationType.REGULAR_PAYMENT,
            command.allocationAmount()
        )).toList();
    }

    private void activatePeriodAndOrders(Long periodId) {
        SubscriptionPeriod period = periodRepository.findWithLockById(periodId)
            .orElseThrow(() -> new IllegalStateException("Regular payment period is missing"));
        List<Order> orders = orderRepository.findAllBySubscriptionPeriodId(periodId);
        if (orders.isEmpty()) throw new IllegalStateException("Regular payment orders are missing");
        period.markScheduled();
        orders.forEach(Order::activateAfterPayment);
    }

    private void failPeriodAndOrders(Long periodId) {
        SubscriptionPeriod period = periodRepository.findWithLockById(periodId)
            .orElseThrow(() -> new IllegalStateException("Regular payment period is missing"));
        List<Order> orders = orderRepository.findAllBySubscriptionPeriodId(periodId);
        if (orders.isEmpty()) throw new IllegalStateException("Regular payment orders are missing");
        period.markPaymentFailed();
        orders.forEach(Order::markPaymentFailed);
    }

    private void requireNoAllocations(List<PaymentAllocationCommand> commands) {
        if (commands != null && !commands.isEmpty()) {
            throw new IllegalArgumentException("A failed regular payment must not create allocations");
        }
    }
}
