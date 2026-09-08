package com.chapchap.subscription.global.kafka.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventJsonSerializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 날짜와_시각을_Source_계약의_ISO_문자열로_직렬화한다() throws Exception {
        JacksonJsonSerializer<TemporalEvent> serializer = new JacksonJsonSerializer<>();
        TemporalEvent event = new TemporalEvent(
            OffsetDateTime.parse("2026-10-08T13:00:00+09:00"),
            LocalDate.parse("2026-10-09")
        );

        String json = new String(serializer.serialize("test-topic", event), StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("occurredAt").isTextual()).isTrue();
        assertThat(root.path("occurredAt").asText()).isEqualTo("2026-10-08T13:00:00+09:00");
        assertThat(root.path("periodStartDate").isTextual()).isTrue();
        assertThat(root.path("periodStartDate").asText()).isEqualTo("2026-10-09");
    }

    private record TemporalEvent(OffsetDateTime occurredAt, LocalDate periodStartDate) {
    }
}
