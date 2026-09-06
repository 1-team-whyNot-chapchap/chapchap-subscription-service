package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.payment.service.FirstPaymentExecutionService;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionService;
import com.chapchap.subscription.domain.payment.service.PreparedSettingChangeRefund;
import com.chapchap.subscription.domain.payment.service.SettingChangePaymentPreparationService;
import com.chapchap.subscription.domain.payment.service.SettingChangeRefundPreparationService;
import com.chapchap.subscription.domain.payment.service.exception.CurrentPaymentMethodUnavailableException;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentExecutionCommand;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import com.chapchap.subscription.domain.subscription.response.SettingChangeResponse;
import com.chapchap.subscription.global.exception.payment.PaymentCancellationFailedException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderAuthenticationFailedException;
import com.chapchap.subscription.global.exception.payment.SettingChangePaymentDeclinedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionChangeConfirmationNotFoundException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SettingChangeService {
    private final SettingChangePreparationFlowService preparation;
    private final SettingChangeAmountService amounts;
    private final SettingChangeFinalizationService finalization;
    private final SettingChangeCompletionService completion;
    private final PaymentMethodRepository paymentMethods;
    private final SettingChangePaymentPreparationService paymentPreparation;
    private final FirstPaymentExecutionService paymentExecution;
    private final SettingChangePaymentCompletionService paymentCompletion;
    private final SettingChangeRefundPreparationService refundPreparation;
    private final PaymentCancellationExecutionService cancellationExecution;
    private final SettingChangeRefundCompletionService refundCompletion;
    private final RefundRepository refunds;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionSettingRepository settings;
    private final KstReferenceTimeProvider time;

    public SettingChangeService(SettingChangePreparationFlowService preparation,
        SettingChangeAmountService amounts, SettingChangeFinalizationService finalization,
        SettingChangeCompletionService completion, PaymentMethodRepository paymentMethods,
        SettingChangePaymentPreparationService paymentPreparation, FirstPaymentExecutionService paymentExecution,
        SettingChangePaymentCompletionService paymentCompletion, SettingChangeRefundPreparationService refundPreparation,
        PaymentCancellationExecutionService cancellationExecution,
        SettingChangeRefundCompletionService refundCompletion, RefundRepository refunds,
        SubscriptionRepository subscriptions, SubscriptionSettingRepository settings,
        KstReferenceTimeProvider time) {
        this.preparation = preparation; this.amounts = amounts; this.finalization = finalization;
        this.completion = completion;
        this.paymentMethods = paymentMethods;
        this.paymentPreparation = paymentPreparation; this.paymentExecution = paymentExecution;
        this.paymentCompletion = paymentCompletion; this.refundPreparation = refundPreparation;
        this.cancellationExecution = cancellationExecution; this.refundCompletion = refundCompletion;
        this.refunds = refunds; this.subscriptions = subscriptions; this.settings = settings; this.time = time;
    }

    public SettingChangeResponse change(Long userId, SettingChangeRequest request) {
        PreparedSettingChange prepared = preparation.prepare(userId, toPreparationRequest(request));
        SettingChangeAmountSnapshot snapshot = amounts.analyze(prepared.settingId());
        if (snapshot.newAmount() == snapshot.oldAmount()) {
            finalization.approve(prepared.settingId(), time.now());
            return response(snapshot, SubscriptionSettingStatus.ACTIVE, false, null, null);
        }
        if (snapshot.newAmount() > snapshot.oldAmount()) {
            com.chapchap.subscription.domain.payment.entity.PaymentMethod method;
            try {
                method = currentPaymentMethod(userId);
            } catch (CurrentPaymentMethodUnavailableException exception) {
                completion.complete(prepared.settingId(), SettingChangeCompletionStatus.NOT_APPLIED, time.now());
                throw exception;
            }
            return response(snapshot, SubscriptionSettingStatus.CHANGE_PENDING, true, method, null);
        }
        return processReduction(snapshot);
    }

    public SettingChangeResponse confirm(Long userId) {
        var subscription = subscriptions.findByUserId(userId).orElseThrow(SubscriptionNotFoundException::new);
        var setting = settings.findTopBySubscriptionIdAndStatusOrderBySettingSequenceDesc(
            subscription.getId(), SubscriptionSettingStatus.CHANGE_PENDING)
            .orElseThrow(SubscriptionChangeConfirmationNotFoundException::new);
        SettingChangeAmountSnapshot snapshot = amounts.analyze(setting.getId());
        if (snapshot.newAmount() <= snapshot.oldAmount()) {
            throw new SubscriptionChangeConfirmationNotFoundException();
        }
        currentPaymentMethod(userId);
        var payment = paymentPreparation.prepare(userId, setting.getId());
        if (payment.getStatus() != PaymentTransactionStatus.PROCESSING) {
            throw new SubscriptionChangeConfirmationNotFoundException();
        }
        var execution = paymentExecution.execute(new FirstPaymentExecutionCommand(payment.getId(), "구독 설정 변경 추가 결제"));
        List<PaymentAllocationCommand> allocations = allocationCommands(snapshot.newOrders(), payment.getTransactionAmount());
        AutomaticPaymentStatus paymentStatus = paymentCompletion.complete(
            setting.getId(), execution, allocations, time.now());
        if (paymentStatus == AutomaticPaymentStatus.DECLINED) {
            throw new SettingChangePaymentDeclinedException();
        }
        if (paymentStatus == AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED) {
            throw new PaymentProviderAuthenticationFailedException();
        }
        return response(snapshot, SubscriptionSettingStatus.ACTIVE, false, null, null);
    }

    private SettingChangeResponse processReduction(SettingChangeAmountSnapshot snapshot) {
        while (true) {
            PreparedSettingChangeRefund prepared = refundPreparation.prepareNext(snapshot.setting().getId());
            if (prepared.status() == RefundStatus.COMPLETED) {
                return response(snapshot, SubscriptionSettingStatus.ACTIVE, false, null,
                    refunds.findById(prepared.refundId()).orElseThrow());
            }
            if (prepared.status() == RefundStatus.REVIEW_REQUIRED) {
                return response(snapshot, SubscriptionSettingStatus.CHANGE_PENDING, false, null,
                    refunds.findById(prepared.refundId()).orElseThrow());
            }
            var execution = cancellationExecution.execute(prepared.cancellationTransactionId());
            RefundStatus status = refundCompletion.complete(snapshot.setting().getId(), execution, time.now());
            if (status == RefundStatus.COMPLETED) {
                return response(snapshot, SubscriptionSettingStatus.ACTIVE, false, null,
                    refunds.findById(prepared.refundId()).orElseThrow());
            }
            if (execution.providerResult().status() == PaymentCancellationStatus.PROVIDER_CONFIGURATION_FAILED) {
                throw new PaymentProviderAuthenticationFailedException();
            }
            if (status == RefundStatus.FAILED) {
                throw new PaymentCancellationFailedException();
            }
            if (status == RefundStatus.REVIEW_REQUIRED) {
                return response(snapshot, SubscriptionSettingStatus.CHANGE_PENDING, false, null,
                    refunds.findById(prepared.refundId()).orElseThrow());
            }
        }
    }

    private com.chapchap.subscription.domain.payment.entity.PaymentMethod currentPaymentMethod(Long userId) {
        return paymentMethods.findByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
            userId, PaymentMethodStatus.AVAILABLE)
            .orElseThrow(CurrentPaymentMethodUnavailableException::new);
    }

    private SettingChangePreparationRequest toPreparationRequest(SettingChangeRequest request) {
        return new SettingChangePreparationRequest(request.planId(), request.deliveryConditions().stream()
            .map(value -> new SettingChangePreparationRequest.DeliveryCondition(value.weekday(), value.mealQuantity(),
                value.addressId(), value.deliveryTimeSlot())).toList());
    }

    private List<PaymentAllocationCommand> allocationCommands(List<Order> orders, long amount) {
        List<PaymentAllocationCommand> result = new ArrayList<>();
        long remaining = amount;
        for (Order order : orders.stream().sorted(Comparator.comparing(Order::getDeliveryDate).thenComparing(Order::getId)).toList()) {
            if (remaining == 0) break;
            long allocated = Math.min(remaining, order.getActualAllocatedAmount());
            result.add(new PaymentAllocationCommand(order.getId(), allocated));
            remaining -= allocated;
        }
        if (remaining != 0) throw new IllegalStateException("Additional payment could not be allocated");
        return result;
    }

    private SettingChangeResponse response(SettingChangeAmountSnapshot snapshot,
        SubscriptionSettingStatus status, boolean confirmationRequired,
        com.chapchap.subscription.domain.payment.entity.PaymentMethod method, Refund refund) {
        var differenceType = snapshot.newAmount() > snapshot.oldAmount()
            ? SettingChangeResponse.DifferenceType.INCREASE
            : snapshot.newAmount() < snapshot.oldAmount()
                ? SettingChangeResponse.DifferenceType.DECREASE
                : SettingChangeResponse.DifferenceType.NO_PRICE_CHANGE;
        var paymentMethod = method == null ? null : new SettingChangeResponse.CurrentPaymentMethod(
            method.getPublicId(), method.getCardCompany(), method.getMaskedCardNumber());
        var refundResult = refund == null ? null : new SettingChangeResponse.RefundResult(
            refund.getPublicId(), refund.getStatus(), refund.getRefundAmount(),
            refund.getSuccessfulRefundAmount(), refund.getRefundAmount() - refund.getSuccessfulRefundAmount());
        return new SettingChangeResponse(status, differenceType, snapshot.differenceAmount(),
            snapshot.setting().getEffectiveStartDate(), confirmationRequired, paymentMethod, refundResult);
    }
}
