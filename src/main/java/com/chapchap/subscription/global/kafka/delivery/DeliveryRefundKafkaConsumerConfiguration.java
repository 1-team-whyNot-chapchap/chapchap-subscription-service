package com.chapchap.subscription.global.kafka.delivery;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class DeliveryRefundKafkaConsumerConfiguration {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> deliveryRefundKafkaListenerContainerFactory(
        KafkaProperties kafkaProperties,
        KafkaTemplate<String, Object> kafkaTemplate,
        DeliveryRefundKafkaProperties deliveryRefundProperties
    ) {
        Map<String, Object> consumerProperties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProperties));
        var recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(deliveryRefundProperties.getDltTopic(), record.partition())
        );
        recoverer.setFailIfSendResultIsError(true);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L)));
        return factory;
    }
}
