package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundCommand;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundContractException;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRefundKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(DeliveryRefundKafkaListener.class);

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .findAndAddModules()
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .build();
    private final DeliveryRefundEventValidator validator;
    private final DeliveryRefundService service;

    public DeliveryRefundKafkaListener(
        DeliveryRefundEventValidator validator,
        DeliveryRefundService service
    ) {
        this.validator = validator;
        this.service = service;
    }

    @KafkaListener(
        topics = "${app.kafka.delivery-refund.topic}",
        groupId = "${app.kafka.delivery-refund.group-id}",
        containerFactory = "deliveryRefundKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        JsonNode root = parseTree(record.value());
        JsonNode eventTypeNode = root.get("eventType");
        if (eventTypeNode == null || !eventTypeNode.isTextual() || eventTypeNode.asText().isBlank()) {
            throw new DeliveryRefundContractException("eventType is required");
        }
        if (!DeliveryRefundConfirmedEvent.EVENT_TYPE.equals(eventTypeNode.asText())) {
            log.info("Ignoring unsupported Delivery eventType={}", eventTypeNode.asText());
            return;
        }

        DeliveryRefundConfirmedEvent event = parseEvent(record.value());
        validator.validate(record.key(), event);
        RefundStatus status = service.process(new DeliveryRefundCommand(
            event.data().deliveryId(), event.data().orderId(), event.userId()));
        log.info("Delivery refund event processed. eventId={}, deliveryId={}, status={}",
            event.eventId(), event.data().deliveryId(), status);
    }

    private JsonNode parseTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DeliveryRefundContractException("Delivery refund event is not valid JSON");
        }
    }

    private DeliveryRefundConfirmedEvent parseEvent(String value) {
        try {
            return objectMapper.readValue(value, DeliveryRefundConfirmedEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DeliveryRefundContractException("Delivery refund event payload is invalid");
        }
    }
}
