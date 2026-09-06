package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.payment.service.DeliveryRefundContractException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryRefundEventValidatorTest {
    private final DeliveryRefundEventValidator validator = new DeliveryRefundEventValidator();

    @Test
    void V1_환불확정_Event의_Key와_필수값을_검증한다() {
        var event = event("11111111-1111-4111-8111-111111111111", "DELIVERY_FAILED");

        assertThatCode(() -> validator.validate(event.data().deliveryId(), event)).doesNotThrowAnyException();
    }

    @Test
    void Key가_deliveryId와_다르면_계약오류다() {
        var event = event("11111111-1111-4111-8111-111111111111", "DELIVERY_FAILED");

        assertThatThrownBy(() -> validator.validate("22222222-2222-4222-8222-222222222222", event))
            .isInstanceOf(DeliveryRefundContractException.class);
    }

    @Test
    void 허용하지_않은_사유와_UUID_v4가_아닌_주문은_계약오류다() {
        var invalidReason = event("11111111-1111-4111-8111-111111111111", "CUSTOMER_CHANGED_MIND");
        assertThatThrownBy(() -> validator.validate(invalidReason.data().deliveryId(), invalidReason))
            .isInstanceOf(DeliveryRefundContractException.class);

        var invalidOrder = new DeliveryRefundConfirmedEvent(
            invalidReason.eventId(), invalidReason.eventType(), 1, invalidReason.occurredAt(), 10L,
            new DeliveryRefundConfirmedEvent.Data(
                invalidReason.data().deliveryId(), "ORD-11111111-1111-1111-8111-111111111111",
                invalidReason.data().confirmedAt(), "DELIVERY_FAILED"));
        assertThatThrownBy(() -> validator.validate(invalidOrder.data().deliveryId(), invalidOrder))
            .isInstanceOf(DeliveryRefundContractException.class);
    }

    private DeliveryRefundConfirmedEvent event(String deliveryId, String reasonCode) {
        return new DeliveryRefundConfirmedEvent(
            "0198b020-6228-7733-8a90-33b36dc39cdf",
            DeliveryRefundConfirmedEvent.EVENT_TYPE,
            1,
            OffsetDateTime.parse("2026-09-06T18:00:00+09:00"),
            10L,
            new DeliveryRefundConfirmedEvent.Data(
                deliveryId,
                "ORD-22222222-2222-4222-8222-222222222222",
                OffsetDateTime.parse("2026-09-06T17:59:00+09:00"),
                reasonCode));
    }
}
