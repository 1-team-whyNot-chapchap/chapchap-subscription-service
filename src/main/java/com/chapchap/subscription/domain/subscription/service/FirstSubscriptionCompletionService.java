package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.FirstOrderService;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.service.FirstPaymentCompletionService;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import com.chapchap.subscription.global.kafka.customer.CustomerPaymentEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 외부 결제의 명시적 결과를 Payment·Subscription·Order에 하나의 트랜잭션으로 확정한다. */
@Service
public class FirstSubscriptionCompletionService {
    private static final String ACTOR = "SYSTEM";

    private final FirstPaymentCompletionService paymentCompletionService;
    private final FirstOrderService firstOrderService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final KstReferenceTimeProvider timeProvider;
    private final AuthSubscriptionStatusPublisher authStatusPublisher;
    private final CustomerPaymentEventPublisher customerPaymentPublisher;

    /** 첫 결제 결과 확정에 참여하는 도메인 서비스와 저장소를 구성한다. */
    public FirstSubscriptionCompletionService(
        FirstPaymentCompletionService paymentCompletionService,
        FirstOrderService firstOrderService,
        SubscriptionRepository subscriptionRepository,
        SubscriptionPeriodRepository periodRepository,
        SubscriptionSettingRepository settingRepository,
        SubscriptionStatusHistoryRepository historyRepository,
        KstReferenceTimeProvider timeProvider,
        AuthSubscriptionStatusPublisher authStatusPublisher,
        CustomerPaymentEventPublisher customerPaymentPublisher
    ) {
        this.paymentCompletionService = paymentCompletionService;
        this.firstOrderService = firstOrderService;
        this.subscriptionRepository = subscriptionRepository;
        this.periodRepository = periodRepository;
        this.settingRepository = settingRepository;
        this.historyRepository = historyRepository;
        this.timeProvider = timeProvider;
        this.authStatusPublisher = authStatusPublisher;
        this.customerPaymentPublisher = customerPaymentPublisher;
    }

    /** 성공이면 시작 예정/활성 상태로, 명시적 실패이면 결제 실패 상태로 함께 변경한다. */
    @Transactional
    public AutomaticPaymentStatus complete(
        PreparedFirstSubscription prepared,
        FirstPaymentExecutionResult executionResult
    ) {
        AutomaticPaymentStatus status = executionResult.providerResult().status();
        List<Long> orderIds = prepared.orderResult().orders().stream()
            .map(order -> order.orderId())
            .toList();
        List<PaymentAllocationCommand> allocations = status == AutomaticPaymentStatus.PAID
            ? prepared.orderResult().orders().stream()
                .map(order -> new PaymentAllocationCommand(order.orderId(), order.actualAllocatedAmount()))
                .toList()
            : List.of();

        paymentCompletionService.complete(executionResult, allocations);

        Subscription subscription = subscriptionRepository.findById(prepared.subscriptionId())
            .orElseThrow(() -> new IllegalStateException("Prepared subscription is missing"));
        SubscriptionPeriod period = periodRepository.findById(prepared.subscriptionPeriodId())
            .orElseThrow(() -> new IllegalStateException("Prepared subscription period is missing"));
        SubscriptionSetting setting = settingRepository.findById(prepared.subscriptionSettingId())
            .orElseThrow(() -> new IllegalStateException("Prepared subscription setting is missing"));
        LocalDateTime changedAt = timeProvider.now();
        SubscriptionStatus previous;
        SubscriptionStatus next;
        String reason;
        if (status == AutomaticPaymentStatus.PAID) {
            previous = subscription.markScheduled();
            period.markScheduled();
            setting.activate(changedAt);
            firstOrderService.activateAfterPayment(prepared.subscriptionPeriodId(), orderIds);
            if (prepared.orderResult().firstDiscountApplied()) {
                subscription.markFirstSubscriptionDiscountUsed();
            }
            next = SubscriptionStatus.SCHEDULED;
            reason = "FIRST_PAYMENT_SUCCEEDED";
        } else {
            previous = subscription.markPaymentFailed();
            period.markPaymentFailed();
            setting.markPaymentFailed();
            firstOrderService.markPaymentFailed(prepared.subscriptionPeriodId(), orderIds);
            next = SubscriptionStatus.PAYMENT_FAILED;
            reason = status == AutomaticPaymentStatus.DECLINED
                ? "FIRST_PAYMENT_DECLINED"
                : "PAYMENT_PROVIDER_CONFIGURATION_FAILED";
        }
        historyRepository.save(SubscriptionStatusHistory.create(
            subscription.getId(), previous, next, ACTOR, reason, changedAt
        ));
        authStatusPublisher.publishAfterCommit(subscription, previous, next, changedAt);
        if (status == AutomaticPaymentStatus.PAID) {
            customerPaymentPublisher.publishCompletedAfterCommit(
                executionResult.paymentTransactionId(), executionResult.respondedAt()
            );
        }
        return status;
    }
}
