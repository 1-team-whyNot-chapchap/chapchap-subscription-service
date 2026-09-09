package com.chapchap.subscription.domain.order.service;

import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;

import java.time.LocalDate;
import java.util.List;

/**
 * 첫 결제 전에 실제 배송일별 주문을 생성하는 데 필요한 확정 입력이다.
 *
 * @param userId 주문 소유 고객의 내부 식별자
 * @param subscriptionId 주문이 속하는 구독 식별자
 * @param subscriptionPeriodId 첫 결제 대상 이용 기간 식별자
 * @param subscriptionSettingId 주문 생성에 적용한 구독 설정 식별자
 * @param termsAgreementId 주문 생성 전에 확인한 비대면 보관 약관 동의 식별자
 * @param plan 주문 생성 당시 플랜과 가격 스냅샷
 * @param applyFirstDiscount 이번 첫 결제 주문에 첫 구독 할인을 적용할지 여부
 * @param periodStartDate 첫 이용 기간 시작일
 * @param periodEndDate 첫 이용 기간 종료일
 * @param deliveries 실제 배송일별 메뉴·수량·배송 조건
 */
public record FirstOrderPreparationCommand(
    Long userId,
    Long subscriptionId,
    Long subscriptionPeriodId,
    Long subscriptionSettingId,
    Long termsAgreementId,
    PlanSnapshot plan,
    boolean applyFirstDiscount,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    List<Delivery> deliveries
) {
    private static final int FIRST_PERIOD_LENGTH_DAYS = 28;
    private static final int MAX_MENU_SEQUENCE = 31;
    private static final int MIN_MEAL_QUANTITY = 1;
    private static final int MAX_MEAL_QUANTITY = 6;

    /** 주문 묶음의 공통 식별자·스냅샷·기간·배송일 입력을 검증한다. */
    public FirstOrderPreparationCommand {
        requirePositive(userId, "userId");
        requirePositive(subscriptionId, "subscriptionId");
        requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
        requirePositive(subscriptionSettingId, "subscriptionSettingId");
        requirePositive(termsAgreementId, "termsAgreementId");
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (periodStartDate == null || periodEndDate == null) {
            throw new IllegalArgumentException("period dates must not be null");
        }
        if (periodEndDate.isBefore(periodStartDate)) {
            throw new IllegalArgumentException("periodEndDate must not be before periodStartDate");
        }
        if (!periodEndDate.equals(periodStartDate.plusDays(FIRST_PERIOD_LENGTH_DAYS - 1L))) {
            throw new IllegalArgumentException("A first subscription period must be exactly 28 days");
        }
        if (deliveries == null || deliveries.isEmpty()) {
            throw new IllegalArgumentException("deliveries must not be empty");
        }
        if (deliveries.stream().anyMatch(delivery -> delivery.address() == null)) {
            throw new IllegalArgumentException("each delivery address must not be null");
        }
        if (deliveries.stream().anyMatch(delivery -> delivery.deliveryTimeSlot() == null)) {
            throw new IllegalArgumentException("each delivery time slot must not be null");
        }
        deliveries = List.copyOf(deliveries);
    }

    /**
     * 로그나 예외 메시지에서 배송 개인정보가 노출되지 않도록 안전한 식별 정보만 반환한다.
     */
    @Override
    public String toString() {
        return "FirstOrderPreparationCommand["
            + "userId=" + userId
            + ", subscriptionId=" + subscriptionId
            + ", subscriptionPeriodId=" + subscriptionPeriodId
            + ", subscriptionSettingId=" + subscriptionSettingId
            + ", termsAgreementId=" + termsAgreementId
            + ", planId=" + plan.planId()
            + ", applyFirstDiscount=" + applyFirstDiscount
            + ", periodStartDate=" + periodStartDate
            + ", periodEndDate=" + periodEndDate
            + ", deliveryCount=" + deliveries.size()
            + ']';
    }

    /** 주문 생성 시점의 플랜명과 가격을 주문에 보존하기 위한 스냅샷이다. */
    public record PlanSnapshot(Long planId, String planName, Long mealUnitPrice) {
        /** 플랜 식별자·이름·가격을 검증한다. */
        public PlanSnapshot {
            requirePositive(planId, "planId");
            requireText(planName, "planName");
            requirePositive(mealUnitPrice, "mealUnitPrice");
        }
    }

    /** 주문 생성 시점의 배송 정보를 주문에 보존하기 위한 스냅샷이다. */
    public record AddressSnapshot(
        Long addressId,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String addressLine1,
        String addressLine2,
        String deliveryMethodCode,
        String otherDeliveryRequest,
        String entrancePassword
    ) {
        /** 필수 배송지 식별자와 수령·주소·배달 방식 값을 검증한다. */
        public AddressSnapshot {
            requirePositive(addressId, "addressId");
            requireText(recipientName, "recipientName");
            requireText(recipientPhone, "recipientPhone");
            requireText(postalCode, "postalCode");
            requireText(addressLine1, "addressLine1");
            requireText(deliveryMethodCode, "deliveryMethodCode");
        }

        /** 개인정보가 자동 출력되지 않도록 배송지 식별자와 배달 방식만 반환한다. */
        @Override
        public String toString() {
            return "AddressSnapshot[addressId=" + addressId
                + ", deliveryMethodCode=" + deliveryMethodCode + ']';
        }
    }

    /**
     * 실제 배송일 한 건에 적용할 메뉴와 도시락 수량이다.
     *
     * @param deliveryDate 실제 배송일
     * @param menuId 배송일에 선택한 메뉴 식별자
     * @param menuPlanId 선택 메뉴가 속한 플랜 식별자
     * @param menuSequence 플랜 내 메뉴 순번
     * @param menuName 주문에 보존할 메뉴명 스냅샷
     * @param mealQuantity 해당 배송일의 도시락 수량
     * @param address 주문 생성 당시 해당 배송일의 배송지 스냅샷
     * @param deliveryTimeSlot 해당 배송일의 배송 시간대
     */
    public record Delivery(
        LocalDate deliveryDate,
        Long menuId,
        Long menuPlanId,
        Integer menuSequence,
        String menuName,
        Integer mealQuantity,
        AddressSnapshot address,
        OrderDeliveryTimeSlot deliveryTimeSlot
    ) {
        /** 배송일·메뉴·수량 값의 기본 형식을 검증한다. */
        public Delivery {
            if (deliveryDate == null) {
                throw new IllegalArgumentException("deliveryDate must not be null");
            }
            requirePositive(menuId, "menuId");
            requirePositive(menuPlanId, "menuPlanId");
            if (menuSequence == null || menuSequence < 1 || menuSequence > MAX_MENU_SEQUENCE) {
                throw new IllegalArgumentException("menuSequence must be between 1 and 31");
            }
            requireText(menuName, "menuName");
            if (mealQuantity == null
                || mealQuantity < MIN_MEAL_QUANTITY
                || mealQuantity > MAX_MEAL_QUANTITY) {
                throw new IllegalArgumentException("mealQuantity must be between 1 and 6");
            }
        }

    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
