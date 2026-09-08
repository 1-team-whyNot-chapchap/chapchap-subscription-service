package com.chapchap.subscription.global.kafka.auth;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthSubscriptionStatusPublisherTest {
    private KafkaTemplate<String, Object> kafkaTemplate;
    private AuthSubscriptionStatusPublisher publisher;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        AuthSubscriptionKafkaProperties properties = new AuthSubscriptionKafkaProperties();
        properties.setTopic("msa4-team1.subscription.subscription-events.v1");
        publisher = new AuthSubscriptionStatusPublisher(kafkaTemplate, properties);
        subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
    }

    @Test
    void 첫_결제_성공은_ACTIVE_순번_1_Event를_사용자_Key로_발행한다() {
        publisher.publishAfterCommit(subscription, SubscriptionStatus.AWAITING_CONFIRMATION,
            SubscriptionStatus.SCHEDULED, LocalDateTime.of(2026, 9, 7, 9, 0));

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        Object[] arguments = mockingDetails(kafkaTemplate).getInvocations().iterator().next().getArguments();
        assertThat(arguments[0]).isEqualTo("msa4-team1.subscription.subscription-events.v1");
        assertThat(arguments[1]).isEqualTo("10");
        SubscriptionStatusChangedEvent event = (SubscriptionStatusChangedEvent) arguments[2];
        assertThat(subscription.getAuthSubscriptionVersion()).isEqualTo(1);
        assertThat(event.eventType()).isEqualTo("SUBSCRIPTION_STATUS_CHANGED");
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.data().subscriptionStatus()).isEqualTo(AuthSubscriptionStatus.ACTIVE);
        assertThat(event.data().subscriptionVersion()).isEqualTo(1);
    }

    @Test
    void Auth_상태가_유지되면_순번과_Event를_바꾸지_않는다() {
        subscription.markScheduled();

        publisher.publishAfterCommit(subscription, SubscriptionStatus.SCHEDULED,
            SubscriptionStatus.IN_PROGRESS, LocalDateTime.of(2026, 9, 7, 0, 0));

        assertThat(subscription.getAuthSubscriptionVersion()).isZero();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void 이용_종료는_ACTIVE_다음_순번의_INACTIVE_Event를_발행한다() {
        subscription.markScheduled();
        subscription.increaseAuthSubscriptionVersion();

        publisher.publishAfterCommit(subscription, SubscriptionStatus.IN_PROGRESS,
            SubscriptionStatus.ENDED, LocalDateTime.of(2026, 10, 4, 0, 0));

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        Object[] arguments = mockingDetails(kafkaTemplate).getInvocations().iterator().next().getArguments();
        SubscriptionStatusChangedEvent event = (SubscriptionStatusChangedEvent) arguments[2];
        assertThat(subscription.getAuthSubscriptionVersion()).isEqualTo(2);
        assertThat(event.data().subscriptionStatus()).isEqualTo(AuthSubscriptionStatus.INACTIVE);
        assertThat(event.data().subscriptionVersion()).isEqualTo(2);
    }
}
