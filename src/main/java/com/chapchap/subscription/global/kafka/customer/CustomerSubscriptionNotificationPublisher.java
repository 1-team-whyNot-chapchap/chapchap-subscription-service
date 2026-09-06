package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;

@Service
public class CustomerSubscriptionNotificationPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CustomerSubscriptionNotificationKafkaProperties properties;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPeriodRepository periods;
    private final PlanRepository plans;
    private final SubscriptionDeliveryConditionRepository conditions;
    private final AddressRepository addresses;

    public CustomerSubscriptionNotificationPublisher(
        KafkaTemplate<String, Object> kafkaTemplate,
        CustomerSubscriptionNotificationKafkaProperties properties,
        SubscriptionRepository subscriptions,
        SubscriptionPeriodRepository periods,
        PlanRepository plans,
        SubscriptionDeliveryConditionRepository conditions,
        AddressRepository addresses
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.periods = periods;
        this.plans = plans;
        this.conditions = conditions;
        this.addresses = addresses;
    }

    public void publishSettingChangedAfterCommit(SubscriptionSetting setting, LocalDateTime occurredAt) {
        Subscription subscription = findSubscription(setting.getSubscriptionId());
        String planName = plans.findById(setting.getPlanId())
            .orElseThrow(() -> new IllegalStateException("Setting event plan is missing"))
            .getName();
        var deliveryConditions = conditions.findAllBySubscriptionSettingId(setting.getId()).stream()
            .sorted(Comparator.comparing(value -> value.getDeliveryWeekday().toDayOfWeek()))
            .map(value -> new SubscriptionSettingChangedEvent.DeliveryCondition(
                value.getDeliveryWeekday().name(),
                value.getMealQuantity(),
                addresses.findById(value.getAddressId())
                    .orElseThrow(() -> new IllegalStateException("Setting event address is missing"))
                    .getName(),
                value.getDeliveryTimeSlot().name()
            ))
            .toList();
        SubscriptionSettingChangedEvent event = new SubscriptionSettingChangedEvent(
            CustomerKafkaPublicationSupport.deterministicEventId(
                SubscriptionSettingChangedEvent.EVENT_TYPE,
                subscription.getPublicId() + ":" + setting.getSettingSequence()
            ),
            SubscriptionSettingChangedEvent.EVENT_TYPE,
            1,
            CustomerKafkaPublicationSupport.toKst(occurredAt),
            subscription.getUserId(),
            new SubscriptionSettingChangedEvent.Data(
                setting.getSettingSequence(), setting.getEffectiveStartDate(), planName, deliveryConditions
            )
        );
        sendAfterCommit(subscription.getUserId(), event);
    }

    public void publishCancellationConfirmedAfterCommit(
        Subscription subscription,
        String cancellationType,
        LocalDateTime requestedAt,
        LocalDateTime effectiveAt,
        LocalDate lastUseDate,
        String refundResult,
        LocalDateTime occurredAt
    ) {
        SubscriptionCancellationConfirmedEvent event = new SubscriptionCancellationConfirmedEvent(
            CustomerKafkaPublicationSupport.deterministicEventId(
                SubscriptionCancellationConfirmedEvent.EVENT_TYPE,
                subscription.getPublicId() + ":" + cancellationType + ":" + requestedAt
            ),
            SubscriptionCancellationConfirmedEvent.EVENT_TYPE,
            1,
            CustomerKafkaPublicationSupport.toKst(occurredAt),
            subscription.getUserId(),
            new SubscriptionCancellationConfirmedEvent.Data(
                cancellationType,
                CustomerKafkaPublicationSupport.toKst(requestedAt),
                CustomerKafkaPublicationSupport.toKst(effectiveAt),
                lastUseDate,
                refundResult
            )
        );
        sendAfterCommit(subscription.getUserId(), event);
    }

    public void publishNextPeriodCancellationAfterCommit(
        Subscription subscription,
        String refundResult,
        LocalDateTime occurredAt
    ) {
        SubscriptionPeriod currentPeriod = periods
            .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                subscription.getId(), SubscriptionPeriodStatus.IN_PROGRESS
            )
            .orElseThrow(() -> new IllegalStateException("Cancellation event current period is missing"));
        LocalDate lastUseDate = currentPeriod.getPeriodEndDate();
        publishCancellationConfirmedAfterCommit(
            subscription,
            "NEXT_PERIOD_CANCELLATION",
            subscription.getCancellationRequestedAt(),
            lastUseDate.plusDays(1).atTime(LocalTime.MIDNIGHT),
            lastUseDate,
            refundResult,
            occurredAt
        );
    }

    public void publishEndedAfterCommit(Subscription subscription, String endReason, LocalDateTime endedAt) {
        SubscriptionEndedEvent event = new SubscriptionEndedEvent(
            CustomerKafkaPublicationSupport.deterministicEventId(
                SubscriptionEndedEvent.EVENT_TYPE, subscription.getPublicId() + ":" + endReason + ":" + endedAt
            ),
            SubscriptionEndedEvent.EVENT_TYPE,
            1,
            CustomerKafkaPublicationSupport.toKst(endedAt),
            subscription.getUserId(),
            new SubscriptionEndedEvent.Data(CustomerKafkaPublicationSupport.toKst(endedAt), endReason)
        );
        sendAfterCommit(subscription.getUserId(), event);
    }

    private Subscription findSubscription(Long id) {
        return subscriptions.findById(id)
            .orElseThrow(() -> new IllegalStateException("Customer notification subscription is missing"));
    }

    private void sendAfterCommit(Long userId, Object event) {
        CustomerKafkaPublicationSupport.afterCommit(
            () -> kafkaTemplate.send(properties.getTopic(), userId.toString(), event)
        );
    }
}
