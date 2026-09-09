package com.chapchap.subscription.global.kafka.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kafka.customer-delivery-address")
public class CustomerDeliveryAddressKafkaProperties {
    private String topic;
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
