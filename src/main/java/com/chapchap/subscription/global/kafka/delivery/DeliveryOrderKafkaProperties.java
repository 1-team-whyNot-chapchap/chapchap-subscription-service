package com.chapchap.subscription.global.kafka.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.kafka.delivery-order")
public class DeliveryOrderKafkaProperties {
    private String topic;
    private Duration sendTimeout = Duration.ofSeconds(10);

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Duration getSendTimeout() { return sendTimeout; }
    public void setSendTimeout(Duration sendTimeout) { this.sendTimeout = sendTimeout; }
}
