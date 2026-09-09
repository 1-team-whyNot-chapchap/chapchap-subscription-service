package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.exception.PaymentTransactionNotFoundException;
import com.chapchap.subscription.domain.payment.service.result.PreparedRegularPayment;
import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
import com.chapchap.subscription.domain.subscription.service.NextSubscriptionPeriodPreparationService;
import com.chapchap.subscription.domain.subscription.service.PreparedNextSubscriptionPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 다음 이용 기간의 오전 정기결제와 오후 재시도 거래를 준비한다. */
@Service
public class RegularPaymentPreparationService {
    private static final String INITIAL_REQUEST_PREFIX = "REGULAR-PAYMENT-09-";
    private static final String RETRY_REQUEST_PREFIX = "REGULAR-PAYMENT-13-";

    private final NextSubscriptionPeriodPreparationService nextPeriodPreparationService;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;

    public RegularPaymentPreparationService(
        NextSubscriptionPeriodPreparationService nextPeriodPreparationService,
        PaymentTransactionRepository transactionRepository,
        OrderRepository orderRepository
    ) {
        this.nextPeriodPreparationService = nextPeriodPreparationService;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
    }

    /** 다음 기간·주문과 정기결제 거래를 하나의 로컬 트랜잭션으로 준비한다. */
    @Transactional
    public Optional<PreparedRegularPayment> prepareInitial(
        Long currentPeriodId,
        LocalDate today,
        LocalDateTime referenceAt
    ) {
        Optional<PreparedNextSubscriptionPeriod> next = nextPeriodPreparationService
            .prepareIfDue(currentPeriodId, today, referenceAt);
        if (next.isEmpty()) return Optional.empty();

        PreparedNextSubscriptionPeriod prepared = next.get();
        String businessKey = PaymentBusinessKeyGenerator.regularPayment(prepared.subscriptionPeriodId());
        Optional<PaymentTransaction> existing = transactionRepository.findByBusinessDeduplicationKey(businessKey);
        if (existing.isPresent()) {
            return Optional.of(toPrepared(existing.get(), prepared.allocations(), false));
        }

        PaymentTransaction transaction = PaymentTransaction.createRegularPayment(
            prepared.userId(), prepared.subscriptionId(), prepared.subscriptionPeriodId(), prepared.totalAmount(),
            prepared.processingReferenceAt(), prepared.periodStartDate(), prepared.periodEndDate(),
            INITIAL_REQUEST_PREFIX + UUID.randomUUID(), referenceAt
        );
        return Optional.of(toPrepared(transactionRepository.save(transaction), prepared.allocations(), true));
    }

    /** 13시 재시도 대기 거래를 조회한다. */
    @Transactional(readOnly = true)
    public List<Long> findRetryWaitingTransactionIds(LocalDate today) {
        LocalDateTime referenceStart = today.atStartOfDay();
        return transactionRepository
            .findAllByStatusAndProcessingReferenceAtGreaterThanEqualAndProcessingReferenceAtLessThanOrderByIdAsc(
                PaymentTransactionStatus.RETRY_WAITING,
                referenceStart,
                referenceStart.plusDays(1)
            )
            .stream().map(PaymentTransaction::getId).toList();
    }

    /** 동일 거래와 금액을 유지하면서 새 외부 요청 키로 재시도를 준비한다. */
    @Transactional
    public PreparedRegularPayment prepareRetry(Long paymentTransactionId) {
        PaymentTransaction transaction = transactionRepository.findWithLockById(paymentTransactionId)
            .orElseThrow(PaymentTransactionNotFoundException::new);
        transaction.startRegularPaymentRetry(RETRY_REQUEST_PREFIX + UUID.randomUUID());
        List<PaymentAllocationCommand> allocations = orderRepository
            .findAllBySubscriptionPeriodId(transaction.getSubscriptionPeriodId()).stream()
            .map(order -> new PaymentAllocationCommand(order.getId(), order.getActualAllocatedAmount()))
            .toList();
        validateAllocationTotal(transaction, allocations);
        return toPrepared(transaction, allocations, true);
    }

    private PreparedRegularPayment toPrepared(
        PaymentTransaction transaction,
        List<PaymentAllocationCommand> allocations,
        boolean paymentRequired
    ) {
        validateAllocationTotal(transaction, allocations);
        return new PreparedRegularPayment(
            transaction.getId(), transaction.getSubscriptionPeriodId(), transaction.getStatus(),
            allocations, paymentRequired
        );
    }

    private void validateAllocationTotal(
        PaymentTransaction transaction,
        List<PaymentAllocationCommand> allocations
    ) {
        long total = allocations.stream()
            .mapToLong(PaymentAllocationCommand::allocationAmount)
            .reduce(0L, Math::addExact);
        if (allocations.isEmpty() || total != transaction.getTransactionAmount()) {
            throw new IllegalStateException("Regular payment allocation total does not match transaction amount");
        }
    }
}
