package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.address.entity.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerDeliveryAddressPublisherTest {
    private KafkaTemplate<String, Object> kafkaTemplate;
    private CustomerDeliveryAddressPublisher publisher;
    private Address address;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        CustomerDeliveryAddressKafkaProperties properties = new CustomerDeliveryAddressKafkaProperties();
        properties.setTopic("subscription.delivery-address-events.v1");
        publisher = new CustomerDeliveryAddressPublisher(kafkaTemplate, properties);
        address = Address.create(10L, "집", "수령인", "01012345678", "12345", "대구 주소", null, "DIRECT", null, null, false);
        ReflectionTestUtils.setField(address, "publicId", "11111111-1111-4111-8111-111111111111");
        ReflectionTestUtils.setField(address, "deliveryAddressVersion", 1L);
    }

    @Test
    void 배송지_변경은_공개_배송지_ID_Key와_최소_Payload로_발행한다() {
        publisher.publishChangedAfterCommit(address, LocalDateTime.of(2026, 9, 7, 10, 0));

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        Object[] args = mockingDetails(kafkaTemplate).getInvocations().iterator().next().getArguments();
        DeliveryAddressChangedEvent event = (DeliveryAddressChangedEvent) args[2];
        assertThat(args[0]).isEqualTo("subscription.delivery-address-events.v1");
        assertThat(args[1]).isEqualTo(address.getPublicId());
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.data().deliveryAddressId()).isEqualTo(address.getPublicId());
        assertThat(event.data().deliveryAddressVersion()).isEqualTo(1L);
        assertThat(event.data().deliveryAddressLabel()).isEqualTo("집");
    }

    @Test
    void 배송지_삭제_거절은_공개_오류코드만_발행한다() {
        publisher.publishRejectedAfterCommit(address, "ADDRESS_004", LocalDateTime.of(2026, 9, 7, 10, 0));

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        Object[] args = mockingDetails(kafkaTemplate).getInvocations().iterator().next().getArguments();
        DeliveryAddressChangeRejectedEvent event = (DeliveryAddressChangeRejectedEvent) args[2];
        assertThat(args[1]).isEqualTo(address.getPublicId());
        assertThat(event.data().deliveryAddressId()).isEqualTo(address.getPublicId());
        assertThat(event.data().rejectionCode()).isEqualTo("ADDRESS_004");
    }
}
