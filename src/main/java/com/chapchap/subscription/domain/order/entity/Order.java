package com.chapchap.subscription.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.GeneratedColumn;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 실제 배송일 한 건에 대응하며 생성 당시 금액·배송 정보를 보존하는 주문이다. */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_public_id", columnNames = "public_id"),
        @UniqueConstraint(
            name = "uk_orders_subscription_date_revision",
            columnNames = {"subscription_id", "delivery_date", "revision_sequence"}
        ),
        @UniqueConstraint(
            name = "uk_orders_active_subscription_date",
            columnNames = {"active_subscription_id", "active_delivery_date"}
        )
    },
    indexes = {
        @Index(name = "idx_orders_user_history", columnList = "user_id, delivery_date DESC, id DESC"),
        @Index(name = "idx_orders_delivery", columnList = "delivery_date, status, kafka_delivery_status, id"),
        @Index(
            name = "idx_orders_period_delivery",
            columnList = "subscription_id, subscription_period_id, delivery_date, status, kafka_delivery_status, id"
        ),
        @Index(name = "idx_orders_address", columnList = "address_id, status, delivery_date")
    }
)
@Check(name = "ck_orders_revision", constraints = "revision_sequence >= 1")
@Check(name = "ck_orders_meal_unit_price", constraints = "meal_unit_price >= 1")
@Check(name = "ck_orders_meal_quantity", constraints = "meal_quantity BETWEEN 1 AND 6")
@Check(name = "ck_orders_meal_amount", constraints = "meal_amount = meal_unit_price * meal_quantity")
@Check(name = "ck_orders_delivery_fee", constraints = "delivery_fee >= 0")
@Check(name = "ck_orders_discount", constraints = "discount_amount >= 0 AND discount_amount <= meal_amount")
@Check(
    name = "ck_orders_actual_amount",
    constraints = "actual_allocated_amount = meal_amount + delivery_fee - discount_amount"
)
@Check(
    name = "ck_orders_delivery_request",
    constraints = "(delivery_method_code = 'OTHER' AND other_delivery_request IS NOT NULL "
        + "AND TRIM(other_delivery_request) <> '') OR "
        + "(delivery_method_code IN ('DIRECT', 'DOORSTEP') AND other_delivery_request IS NULL)"
)
@Check(
    name = "ck_orders_kafka_stored_at",
    constraints = "(kafka_delivery_status = 'COMPLETED' AND kafka_stored_at IS NOT NULL) OR "
        + "(kafka_delivery_status <> 'COMPLETED' AND kafka_stored_at IS NULL)"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    private static final String PUBLIC_ID_PREFIX = "ORD-";
    private static final String DELIVERY_METHOD_DIRECT = "DIRECT";
    private static final String DELIVERY_METHOD_DOORSTEP = "DOORSTEP";
    private static final String DELIVERY_METHOD_OTHER = "OTHER";
    private static final int MIN_MEAL_QUANTITY = 1;
    private static final int MAX_MEAL_QUANTITY = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 40, columnDefinition = "CHAR(40)")
    private String publicId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Column(name = "subscription_period_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionPeriodId;

    @Column(name = "subscription_setting_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionSettingId;

    @Column(name = "non_face_to_face_terms_agreement_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long nonFaceToFaceTermsAgreementId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long planId;

    @Column(name = "address_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long addressId;

    @Column(name = "menu_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long menuId;

    @Column(name = "replacement_target_order_id", columnDefinition = "BIGINT UNSIGNED")
    private Long replacementTargetOrderId;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "revision_sequence", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer revisionSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kafka_delivery_status", nullable = false, length = 30)
    private OrderKafkaDeliveryStatus kafkaDeliveryStatus;

    @GeneratedColumn("CASE WHEN status = 'ACTIVE' THEN subscription_id ELSE NULL END")
    @Column(name = "active_subscription_id", columnDefinition = "BIGINT UNSIGNED")
    private Long activeSubscriptionId;

    @GeneratedColumn("CASE WHEN status = 'ACTIVE' THEN delivery_date ELSE NULL END")
    @Column(name = "active_delivery_date")
    private LocalDate activeDeliveryDate;

    @Column(name = "plan_name", nullable = false, length = 50)
    private String planName;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(name = "meal_unit_price", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long mealUnitPrice;

    @Column(name = "meal_quantity", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer mealQuantity;

    @Column(name = "meal_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long mealAmount;

    @Column(name = "delivery_fee", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long deliveryFee;

    @Column(name = "discount_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long discountAmount;

    @Column(name = "actual_allocated_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long actualAllocatedAmount;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "delivery_method_code", nullable = false, length = 20)
    private String deliveryMethodCode;

    @Column(name = "other_delivery_request", length = 255)
    private String otherDeliveryRequest;

    @Column(name = "entrance_password", length = 100)
    private String entrancePassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_time_slot", nullable = false, length = 20)
    private OrderDeliveryTimeSlot deliveryTimeSlot;

    @Column(name = "kafka_stored_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime kafkaStoredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    /**
     * 첫 결제 전에 확정 대기 상태의 배송일별 주문을 생성한다.
     *
     * @param userId 주문 소유 고객 식별자
     * @param subscriptionId 구독 식별자
     * @param subscriptionPeriodId 이용 기간 식별자
     * @param subscriptionSettingId 적용 설정 식별자
     * @param termsAgreementId 비대면 보관 약관 동의 식별자
     * @param planId 플랜 식별자
     * @param addressId 배송지 식별자
     * @param menuId 메뉴 식별자
     * @param deliveryDate 실제 배송일
     * @param planName 플랜명 스냅샷
     * @param menuName 메뉴명 스냅샷
     * @param mealUnitPrice 도시락 한 개 가격
     * @param mealQuantity 도시락 수량
     * @param mealAmount 할인 전 도시락 금액
     * @param deliveryFee 배송비
     * @param discountAmount 주문에 적용한 할인금액
     * @param actualAllocatedAmount 결제에서 주문에 배분할 실제 금액
     * @param recipientName 수령인 이름 스냅샷
     * @param recipientPhone 수령인 연락처 스냅샷
     * @param postalCode 우편번호 스냅샷
     * @param addressLine1 기본 주소 스냅샷
     * @param addressLine2 선택 상세 주소 스냅샷
     * @param deliveryMethodCode 배달 방식 코드 스냅샷
     * @param otherDeliveryRequest OTHER 배달 방식의 직접 입력 내용
     * @param entrancePassword 선택 공동현관 비밀번호
     * @param deliveryTimeSlot 배송 시간대 스냅샷
     * @return 결제 결과를 기다리는 최초 주문
     */
    @Builder(builderMethodName = "awaitingConfirmationBuilder")
    public static Order createAwaitingConfirmation(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long subscriptionSettingId,
        Long termsAgreementId,
        Long planId,
        Long addressId,
        Long menuId,
        LocalDate deliveryDate,
        String planName,
        String menuName,
        Long mealUnitPrice,
        Integer mealQuantity,
        Long mealAmount,
        Long deliveryFee,
        Long discountAmount,
        Long actualAllocatedAmount,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethodCode,
        String otherDeliveryRequest,
        String entrancePassword,
        OrderDeliveryTimeSlot deliveryTimeSlot
    ) {
        Order order = new Order();
        order.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        order.userId = requirePositive(userId, "userId");
        order.subscriptionId = requirePositive(subscriptionId, "subscriptionId");
        order.subscriptionPeriodId = requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        order.subscriptionSettingId = requirePositive(subscriptionSettingId, "subscriptionSettingId");
        order.nonFaceToFaceTermsAgreementId = requirePositive(termsAgreementId, "termsAgreementId");
        order.planId = requirePositive(planId, "planId");
        order.addressId = requirePositive(addressId, "addressId");
        order.menuId = requirePositive(menuId, "menuId");
        order.deliveryDate = requireNonNull(deliveryDate, "deliveryDate");
        order.revisionSequence = 1;
        order.status = OrderStatus.AWAITING_CONFIRMATION;
        order.kafkaDeliveryStatus = OrderKafkaDeliveryStatus.NOT_SENT;
        order.planName = requireText(planName, "planName");
        order.menuName = requireText(menuName, "menuName");
        order.mealUnitPrice = requirePositive(mealUnitPrice, "mealUnitPrice");
        order.mealQuantity = requireQuantity(mealQuantity);
        order.mealAmount = requirePositive(mealAmount, "mealAmount");
        order.deliveryFee = requireNonNegative(deliveryFee, "deliveryFee");
        order.discountAmount = requireNonNegative(discountAmount, "discountAmount");
        order.actualAllocatedAmount = requirePositive(actualAllocatedAmount, "actualAllocatedAmount");
        order.recipientName = requireText(recipientName, "recipientName");
        order.recipientPhone = requireText(recipientPhone, "recipientPhone");
        order.postalCode = requireText(postalCode, "postalCode");
        order.addressLine1 = requireText(addressLine1, "addressLine1");
        order.addressLine2 = normalizeNullable(addressLine2);
        order.deliveryMethodCode = requireDeliveryMethod(deliveryMethodCode);
        order.otherDeliveryRequest = normalizeNullable(otherDeliveryRequest);
        order.entrancePassword = normalizeNullable(entrancePassword);
        order.deliveryTimeSlot = requireNonNull(deliveryTimeSlot, "deliveryTimeSlot");
        order.validatePersistentState();
        return order;
    }

    /**
     * 설정 변경 결과를 기다리는 주문을 만든다.
     * 같은 배송일의 기존 유효 주문을 바꾸는 경우에만 {@code replacementTargetOrderId}를 기록한다.
     */
    public static Order createChangePending(
        Long userId,
        Long subscriptionId,
        Long subscriptionPeriodId,
        Long subscriptionSettingId,
        Long termsAgreementId,
        Long planId,
        Long addressId,
        Long menuId,
        LocalDate deliveryDate,
        int revisionSequence,
        Long replacementTargetOrderId,
        String planName,
        String menuName,
        Long mealUnitPrice,
        Integer mealQuantity,
        Long mealAmount,
        Long deliveryFee,
        Long discountAmount,
        Long actualAllocatedAmount,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethodCode,
        String otherDeliveryRequest,
        String entrancePassword,
        OrderDeliveryTimeSlot deliveryTimeSlot
    ) {
        if (revisionSequence < 1) {
            throw new IllegalArgumentException("revisionSequence must be positive");
        }
        if (replacementTargetOrderId != null && replacementTargetOrderId <= 0) {
            throw new IllegalArgumentException("replacementTargetOrderId must be positive when present");
        }
        Order order = createAwaitingConfirmation(
            userId, subscriptionId, subscriptionPeriodId, subscriptionSettingId, termsAgreementId, planId, addressId,
            menuId, deliveryDate, planName, menuName, mealUnitPrice, mealQuantity, mealAmount, deliveryFee,
            discountAmount, actualAllocatedAmount, recipientName, recipientPhone, postalCode, addressLine1,
            addressLine2, deliveryMethodCode, otherDeliveryRequest, entrancePassword, deliveryTimeSlot
        );
        order.revisionSequence = revisionSequence;
        order.replacementTargetOrderId = replacementTargetOrderId;
        order.status = OrderStatus.CHANGE_PENDING;
        return order;
    }

    /** 첫 결제 성공 뒤 확정 대기 주문을 유효 상태로 변경한다. */
    public void activateAfterPayment() {
        requireAwaitingConfirmation();
        status = OrderStatus.ACTIVE;
    }

    /** 첫 결제 거절 뒤 확정 대기 주문을 결제 실패 상태로 변경한다. */
    public void markPaymentFailed() {
        requireAwaitingConfirmation();
        status = OrderStatus.PAYMENT_FAILED;
    }

    /** 설정 변경 확정 후 새 주문을 유효 상태로 바꾼다. */
    public void activateChange() {
        if (status != OrderStatus.CHANGE_PENDING) {
            throw new IllegalStateException("Only a change pending order can be activated");
        }
        status = OrderStatus.ACTIVE;
    }

    /** 설정 변경 결제·환불 실패 후 새 주문이 적용되지 않았음을 기록한다. */
    public void markChangeNotApplied() {
        if (status != OrderStatus.CHANGE_PENDING) {
            throw new IllegalStateException("Only a change pending order can be marked not applied");
        }
        status = OrderStatus.CHANGE_NOT_APPLIED;
    }

    /** 설정 변경 확정 때 아직 Delivery에 전달되지 않은 기존 유효 주문을 비활성화한다. */
    public void inactivateForSettingChange() {
        if (status != OrderStatus.ACTIVE || kafkaDeliveryStatus == OrderKafkaDeliveryStatus.COMPLETED) {
            throw new IllegalStateException("Only an unsent active order can be replaced");
        }
        status = OrderStatus.INACTIVE;
    }

    /** Kafka Broker 저장 성공을 반영한다. */
    public void markKafkaDeliveryCompleted(LocalDateTime storedAt) {
        if (kafkaDeliveryStatus != OrderKafkaDeliveryStatus.NOT_SENT
            && kafkaDeliveryStatus != OrderKafkaDeliveryStatus.FAILED) {
            throw new IllegalStateException("Only unsent or failed delivery can be completed");
        }
        kafkaDeliveryStatus = OrderKafkaDeliveryStatus.COMPLETED;
        kafkaStoredAt = requireNonNull(storedAt, "storedAt");
    }

    /** 15시 최초 Kafka 저장 실패를 반영한다. */
    public void markKafkaDeliveryFailed() {
        if (kafkaDeliveryStatus != OrderKafkaDeliveryStatus.NOT_SENT) {
            throw new IllegalStateException("Only unsent delivery can fail initially");
        }
        kafkaDeliveryStatus = OrderKafkaDeliveryStatus.FAILED;
        kafkaStoredAt = null;
    }

    /** 16시 재시도까지 실패한 Kafka 전달을 종료 상태로 반영한다. */
    public void markKafkaDeliveryFinalFailed() {
        if (kafkaDeliveryStatus != OrderKafkaDeliveryStatus.FAILED) {
            throw new IllegalStateException("Only failed delivery can become final failed");
        }
        kafkaDeliveryStatus = OrderKafkaDeliveryStatus.FINAL_FAILED;
        kafkaStoredAt = null;
    }

    /** DB 반영 직전에도 금액·배송·Kafka 상태 불변식을 다시 확인한다. */
    @PrePersist
    @PreUpdate
    private void validatePersistentState() {
        validateAmounts();
        validateDeliveryRequest();
        validateKafkaDeliveryState();
    }

    private void validateAmounts() {
        if (Math.multiplyExact(mealUnitPrice, mealQuantity.longValue()) != mealAmount) {
            throw new IllegalArgumentException("mealAmount must equal mealUnitPrice multiplied by mealQuantity");
        }
        if (discountAmount > mealAmount) {
            throw new IllegalArgumentException("discountAmount must not exceed mealAmount");
        }
        long expectedActualAmount = Math.subtractExact(
            Math.addExact(mealAmount, deliveryFee),
            discountAmount
        );
        if (expectedActualAmount != actualAllocatedAmount) {
            throw new IllegalArgumentException("actualAllocatedAmount does not match the order amount formula");
        }
    }

    private void validateDeliveryRequest() {
        if (DELIVERY_METHOD_OTHER.equals(deliveryMethodCode) && otherDeliveryRequest == null) {
            throw new IllegalArgumentException("otherDeliveryRequest is required for OTHER delivery method");
        }
        if (!DELIVERY_METHOD_OTHER.equals(deliveryMethodCode) && otherDeliveryRequest != null) {
            throw new IllegalArgumentException("otherDeliveryRequest is allowed only for OTHER delivery method");
        }
    }

    private void validateKafkaDeliveryState() {
        boolean completed = kafkaDeliveryStatus == OrderKafkaDeliveryStatus.COMPLETED;
        if (completed != (kafkaStoredAt != null)) {
            throw new IllegalStateException(
                "kafkaStoredAt must exist only when kafkaDeliveryStatus is COMPLETED"
            );
        }
    }

    private void requireAwaitingConfirmation() {
        if (status != OrderStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("Only an awaiting confirmation order can be completed");
        }
    }

    private static String requireDeliveryMethod(String value) {
        String method = requireText(value, "deliveryMethodCode");
        if (!method.equals(DELIVERY_METHOD_DIRECT)
            && !method.equals(DELIVERY_METHOD_DOORSTEP)
            && !method.equals(DELIVERY_METHOD_OTHER)) {
            throw new IllegalArgumentException("Unsupported deliveryMethodCode");
        }
        return method;
    }

    private static Integer requireQuantity(Integer value) {
        if (value == null || value < MIN_MEAL_QUANTITY || value > MAX_MEAL_QUANTITY) {
            throw new IllegalArgumentException("mealQuantity must be between 1 and 6");
        }
        return value;
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Long requireNonNegative(Long value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
