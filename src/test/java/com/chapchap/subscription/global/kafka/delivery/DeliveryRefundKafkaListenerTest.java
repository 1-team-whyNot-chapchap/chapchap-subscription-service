package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryRefundKafkaListenerTest {
    @Test
    void 정상_JSON을_검증해_업무서비스에_전달한다() {
        DeliveryRefundService service = mock(DeliveryRefundService.class);
        when(service.process(any())).thenReturn(RefundStatus.COMPLETED);
        var listener = new DeliveryRefundKafkaListener(new DeliveryRefundEventValidator(), service);
        String deliveryId = "11111111-1111-4111-8111-111111111111";
        String json = """
            {"eventId":"0198b020-6228-7733-8a90-33b36dc39cdf","eventType":"DELIVERY_REFUND_CONFIRMED",
             "version":1,"occurredAt":"2026-09-06T18:00:00+09:00","userId":10,
             "data":{"deliveryId":"11111111-1111-4111-8111-111111111111",
             "orderId":"ORD-22222222-2222-4222-8222-222222222222",
             "confirmedAt":"2026-09-06T17:59:00+09:00","reasonCode":"DELIVERY_FAILED"}}
            """;

        assertThatCode(() -> listener.consume(new ConsumerRecord<>("topic", 0, 0L, deliveryId, json)))
            .doesNotThrowAnyException();
        verify(service).process(any());
    }

    @Test
    void 알_수_없는_Event_Type은_업무처리_없이_정상_종료한다() {
        DeliveryRefundService service = mock(DeliveryRefundService.class);
        var listener = new DeliveryRefundKafkaListener(new DeliveryRefundEventValidator(), service);

        listener.consume(new ConsumerRecord<>("topic", 0, 0L, "key", "{\"eventType\":\"OTHER_EVENT\"}"));

        verify(service, never()).process(any());
    }
}
