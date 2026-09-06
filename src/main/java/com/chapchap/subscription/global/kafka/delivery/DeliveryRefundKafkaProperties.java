package com.chapchap.subscription.global.kafka.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kafka.delivery-refund")
public class DeliveryRefundKafkaProperties {
    private String topic;
    private String dltTopic;
    private String groupId;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getDltTopic() { return dltTopic; }
    public void setDltTopic(String dltTopic) { this.dltTopic = dltTopic; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
}
