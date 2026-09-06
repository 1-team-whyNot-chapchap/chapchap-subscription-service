package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.payment.service.DeliveryRefundContractException;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Component
public class DeliveryRefundEventValidator {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final Set<String> REASON_CODES = Set.of(
        "DELIVERY_DELAYED", "DELIVERY_FAILED", "FORCE_MAJEURE_CANCELED");

    public void validate(String messageKey, DeliveryRefundConfirmedEvent event) {
        if (event == null) fail("Event body is required");
        requireCanonicalUuid(event.eventId(), "eventId");
        if (!DeliveryRefundConfirmedEvent.EVENT_TYPE.equals(event.eventType())) fail("Unsupported eventType");
        if (event.version() == null || event.version() != 1) fail("Unsupported event version");
        requireKst(event.occurredAt(), "occurredAt");
        if (event.userId() == null || event.userId() <= 0) fail("userId must be positive");
        if (event.data() == null) fail("data is required");

        requireCanonicalUuid(event.data().deliveryId(), "deliveryId");
        if (!event.data().deliveryId().equals(messageKey)) fail("Message key and deliveryId do not match");
        requireOrderId(event.data().orderId());
        requireKst(event.data().confirmedAt(), "confirmedAt");
        if (!REASON_CODES.contains(event.data().reasonCode())) fail("Unsupported reasonCode");
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || !orderId.startsWith("ORD-")) fail("orderId must use ORD-{UUID v4}");
        UUID uuid = parseCanonicalUuid(orderId.substring(4), "orderId");
        if (uuid.version() != 4) fail("orderId must use UUID v4");
    }

    private void requireCanonicalUuid(String value, String field) {
        parseCanonicalUuid(value, field);
    }

    private UUID parseCanonicalUuid(String value, String field) {
        if (value == null || value.isBlank()) fail(field + " is required");
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase())) fail(field + " must be a canonical UUID");
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new DeliveryRefundContractException(field + " must be a canonical UUID");
        }
    }

    private void requireKst(java.time.OffsetDateTime value, String field) {
        if (value == null || !KST_OFFSET.equals(value.getOffset())) fail(field + " must include +09:00 offset");
    }

    private void fail(String message) {
        throw new DeliveryRefundContractException(message);
    }
}
