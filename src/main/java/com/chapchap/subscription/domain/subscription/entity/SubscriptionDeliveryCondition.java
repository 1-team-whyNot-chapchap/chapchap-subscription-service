package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "subscription_delivery_conditions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_delivery_conditions_setting_weekday",
                columnNames = {"subscription_setting_id", "delivery_weekday"}
        ),
        indexes = @Index(
                name = "idx_subscription_delivery_conditions_address_id",
                columnList = "address_id"
        ),
        check = {
                @CheckConstraint(
                        name = "chk_subscription_delivery_conditions_weekday",
                        constraint = "delivery_weekday IN "
                                + "('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY')"
                ),
                @CheckConstraint(
                        name = "chk_subscription_delivery_conditions_quantity",
                        constraint = "meal_quantity BETWEEN 1 AND 6"
                ),
                @CheckConstraint(
                        name = "chk_subscription_delivery_conditions_time_slot",
                        constraint = "delivery_time_slot IN ('TIME_1100_1300', 'TIME_1700_1900')"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionDeliveryCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "subscription_setting_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionSettingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_weekday", nullable = false, length = 10)
    private DeliveryWeekday deliveryWeekday;

    @Column(name = "meal_quantity", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer mealQuantity;

    @Column(name = "address_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long addressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_time_slot", nullable = false, length = 20)
    private DeliveryTimeSlot deliveryTimeSlot;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    // 요일별 배송 조건 새 데이터 추가
    public static SubscriptionDeliveryCondition create(
            Long subscriptionSettingId,
            DeliveryWeekday deliveryWeekday,
            int mealQuantity,
            Long addressId,
            DeliveryTimeSlot deliveryTimeSlot
    ) {
        if (subscriptionSettingId == null || subscriptionSettingId <= 0 || addressId == null || addressId <= 0) {
            throw new IllegalArgumentException("설정과 배송지 식별자는 양수여야 합니다.");
        }
        if (deliveryWeekday == null || deliveryTimeSlot == null || mealQuantity < 1 || mealQuantity > 6) {
            throw new IllegalArgumentException("요일별 배송 조건이 정책 범위를 벗어났습니다.");
        }

        SubscriptionDeliveryCondition condition = new SubscriptionDeliveryCondition();
        condition.subscriptionSettingId = subscriptionSettingId;
        condition.deliveryWeekday = deliveryWeekday;
        condition.mealQuantity = mealQuantity;
        condition.addressId = addressId;
        condition.deliveryTimeSlot = deliveryTimeSlot;
        return condition;
    }
}
