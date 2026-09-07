package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationClient;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationRequest;
import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryRefundIntegrationTest {
    @Autowired private DeliveryRefundService service;
    @Autowired private OrderRepository orders;
    @Autowired private RefundRepository refunds;
    @Autowired private PaymentTransactionRepository payments;
    @Autowired private PaymentAttemptRepository attempts;
    @Autowired private PaymentAllocationRepository allocations;
    @Autowired private EntityManager entityManager;

    @MockitoBean
    private PaymentCancellationClient cancellationClient;

    @Test
    void 저장된_주문금액을_환불하고_같은_deliveryId_재수신에는_PG를_재호출하지_않는다() {
        Fixture fixture = fixture(8_000L);
        String deliveryId = UUID.randomUUID().toString();
        when(cancellationClient.cancel(any())).thenReturn(
            PaymentCancellationResult.succeeded("external-payment-1", "external-cancellation-1"));

        assertThat(service.process(new DeliveryRefundCommand(deliveryId, fixture.order.getPublicId(), 10L)))
            .isEqualTo(RefundStatus.COMPLETED);
        assertThat(service.process(new DeliveryRefundCommand(deliveryId, fixture.order.getPublicId(), 10L)))
            .isEqualTo(RefundStatus.COMPLETED);
        entityManager.flush();

        var refund = refunds.findByExternalDeliveryId(deliveryId).orElseThrow();
        assertThat(refund.getRefundAmount()).isEqualTo(8_000L);
        assertThat(refund.getSuccessfulRefundAmount()).isEqualTo(8_000L);
        assertThat(orders.findById(fixture.order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ACTIVE);
        verify(cancellationClient, times(1)).cancel(any());
    }

    @Test
    void 여러_원결제면_최근_설정변경_추가결제부터_순차_취소한다() {
        Fixture fixture = fixture(5_000L);
        PaymentTransaction change = settingChangeOriginal(fixture, 3_000L);
        allocations.saveAndFlush(PaymentAllocation.create(
            fixture.order.getId(), change.getId(), PaymentAllocationType.SETTING_CHANGE_PAYMENT, 3_000L));
        List<String> calledPayments = new java.util.ArrayList<>();
        when(cancellationClient.cancel(any())).thenAnswer(invocation -> {
            PaymentCancellationRequest request = invocation.getArgument(0);
            calledPayments.add(request.externalPaymentId());
            return PaymentCancellationResult.succeeded(
                request.externalPaymentId(), "cancel-" + calledPayments.size());
        });

        assertThat(service.process(new DeliveryRefundCommand(
            UUID.randomUUID().toString(), fixture.order.getPublicId(), 10L)))
            .isEqualTo(RefundStatus.COMPLETED);

        assertThat(calledPayments).containsExactly("external-change", "external-first");
        assertThat(allocations.findAllByOrderIdOrderByIdAsc(fixture.order.getId()))
            .extracting(PaymentAllocation::currentCancelableAmount)
            .containsExactly(0L, 0L);
    }

    private Fixture fixture(long firstAmount) {
        long unique = uniqueId();
        Order order = Order.createAwaitingConfirmation(
            10L, unique, uniqueId(), uniqueId(), uniqueId(), uniqueId(), uniqueId(), uniqueId(),
            LocalDate.of(2026, 9, 8), "통합 테스트 플랜", "통합 테스트 메뉴",
            4_000L, 2, 8_000L, 0L, 0L, 8_000L,
            "테스트 고객", "010-0000-0000", "41911", "테스트 주소", null,
            "DOORSTEP", null, null, OrderDeliveryTimeSlot.TIME_1100_1300);
        order.activateAfterPayment();
        orders.saveAndFlush(order);

        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 9, 0);
        PaymentTransaction first = PaymentTransaction.createFirstSubscriptionPayment(
            10L, unique, order.getSubscriptionPeriodId(), firstAmount, now,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
            UUID.randomUUID().toString(), now);
        first.markAsSucceeded();
        payments.saveAndFlush(first);
        attempts.saveAndFlush(PaymentAttempt.success(
            first.getId(), uniqueId(), PaymentProviderCode.PORTONE, 1, UUID.randomUUID().toString(),
            firstAmount, now, now.plusSeconds(1), "external-first", "tx-first", "SUCCEEDED"));
        allocations.saveAndFlush(PaymentAllocation.create(
            order.getId(), first.getId(), PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, firstAmount));
        return new Fixture(order, first);
    }

    private PaymentTransaction settingChangeOriginal(Fixture fixture, long amount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 12, 0);
        PaymentTransaction change = PaymentTransaction.createSettingChangePayment(
            10L, fixture.order.getSubscriptionId(), fixture.order.getSubscriptionPeriodId(), uniqueId(),
            amount, now, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 6), UUID.randomUUID().toString(), now);
        change.markAsSucceeded();
        payments.saveAndFlush(change);
        attempts.saveAndFlush(PaymentAttempt.success(
            change.getId(), uniqueId(), PaymentProviderCode.PORTONE, 1, UUID.randomUUID().toString(),
            amount, now, now.plusSeconds(1), "external-change", "tx-change", "SUCCEEDED"));
        return change;
    }

    private long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private record Fixture(Order order, PaymentTransaction first) {
    }
}
