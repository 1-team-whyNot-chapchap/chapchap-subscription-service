package com.chapchap.subscription.global.kafka.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kafka.auth-subscription-status")
public class AuthSubscriptionKafkaProperties {
    private String topic;
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
