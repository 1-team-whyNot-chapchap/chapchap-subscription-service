package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryRefundServiceTest {
    @Test
    void 중복_Event는_기존_상태만_반환하고_PG를_호출하지_않는다() {
        var preparation = mock(DeliveryRefundPreparationService.class);
        var execution = mock(PaymentCancellationExecutionService.class);
        var completion = mock(DeliveryRefundCancellationCompletionService.class);
        var service = new DeliveryRefundService(preparation, execution, completion);
        var command = new DeliveryRefundCommand("delivery-id", "order-id", 10L);
        when(preparation.start(command)).thenReturn(
            new PreparedDeliveryRefund(1L, RefundStatus.PENDING, null, true));

        assertThat(service.process(command)).isEqualTo(RefundStatus.PENDING);
        verify(execution, never()).execute(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 여러_원결제를_순차_취소하고_모두_성공하면_완료한다() {
        var preparation = mock(DeliveryRefundPreparationService.class);
        var execution = mock(PaymentCancellationExecutionService.class);
        var completion = mock(DeliveryRefundCancellationCompletionService.class);
        var service = new DeliveryRefundService(preparation, execution, completion);
        var command = new DeliveryRefundCommand("delivery-id", "order-id", 10L);
        when(preparation.start(command)).thenReturn(
            new PreparedDeliveryRefund(1L, RefundStatus.PENDING, 11L, false));
        when(preparation.prepareNext(1L)).thenReturn(
            new PreparedDeliveryRefund(1L, RefundStatus.PENDING, 12L, false));
        var first = result(11L, "key-1");
        var second = result(12L, "key-2");
        when(execution.execute(11L)).thenReturn(first);
        when(execution.execute(12L)).thenReturn(second);
        when(completion.complete(first)).thenReturn(RefundStatus.PENDING);
        when(completion.complete(second)).thenReturn(RefundStatus.COMPLETED);

        assertThat(service.process(command)).isEqualTo(RefundStatus.COMPLETED);
        verify(execution).execute(11L);
        verify(execution).execute(12L);
    }

    @Test
    void 첫_명시적_실패면_다음_원결제를_준비하지_않는다() {
        var preparation = mock(DeliveryRefundPreparationService.class);
        var execution = mock(PaymentCancellationExecutionService.class);
        var completion = mock(DeliveryRefundCancellationCompletionService.class);
        var service = new DeliveryRefundService(preparation, execution, completion);
        var command = new DeliveryRefundCommand("delivery-id", "order-id", 10L);
        var result = result(11L, "key-1");
        when(preparation.start(command)).thenReturn(
            new PreparedDeliveryRefund(1L, RefundStatus.PENDING, 11L, false));
        when(execution.execute(11L)).thenReturn(result);
        when(completion.complete(result)).thenReturn(RefundStatus.FAILED);

        assertThat(service.process(command)).isEqualTo(RefundStatus.FAILED);
        verify(preparation, never()).prepareNext(1L);
    }

    private PaymentCancellationExecutionResult result(Long id, String key) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 18, 0);
        return new PaymentCancellationExecutionResult(
            id, PaymentProviderCode.PORTONE, key, 1_000L, now, now.plusSeconds(1),
            PaymentCancellationResult.succeeded("payment-id", "cancellation-id"));
    }
}
