package com.chapchap.subscription.domain.order.service;

/** 첫 구독 주문 한 건의 도시락·배송비·첫 할인 금액을 같은 규칙으로 계산한다. */
public final class FirstOrderAmountCalculator {
    /** 실제 배송 주문 한 건에 부과하는 고정 배송비다. */
    public static final long DELIVERY_FEE = 3_000L;

    private FirstOrderAmountCalculator() {
    }

    /**
     * 주문 한 건의 금액 구성을 계산한다.
     *
     * <p>첫 할인은 주문마다 도시락 한 개의 단가에만 적용한다. 이 계산은 Preview와 실제 주문 생성이
     * 함께 사용하므로, 호출자는 반환값을 별도로 재계산하지 않는다.</p>
     */
    public static FirstOrderAmount calculate(
        long mealUnitPrice,
        int mealQuantity,
        boolean applyFirstDiscount
    ) {
        if (mealUnitPrice <= 0L) {
            throw new IllegalArgumentException("mealUnitPrice must be positive");
        }
        if (mealQuantity < 1 || mealQuantity > 6) {
            throw new IllegalArgumentException("mealQuantity must be between 1 and 6");
        }

        long mealAmount = Math.multiplyExact(mealUnitPrice, mealQuantity);
        long discountAmount = applyFirstDiscount
            ? FirstSubscriptionDiscountCalculator.calculate(mealUnitPrice)
            : 0L;
        long actualAllocatedAmount = Math.subtractExact(
            Math.addExact(mealAmount, DELIVERY_FEE),
            discountAmount
        );
        return new FirstOrderAmount(mealAmount, DELIVERY_FEE, discountAmount, actualAllocatedAmount);
    }

    /** 주문 한 건의 금액 구성 스냅샷이다. */
    public record FirstOrderAmount(
        long mealAmount,
        long deliveryFee,
        long discountAmount,
        long actualAllocatedAmount
    ) {
        /** 금액의 양수·0 허용 범위와 합계 관계를 검증한다. */
        public FirstOrderAmount {
            if (mealAmount <= 0L || deliveryFee < 0L || discountAmount < 0L || actualAllocatedAmount <= 0L) {
                throw new IllegalArgumentException("order amounts must be valid");
            }
            if (actualAllocatedAmount != Math.subtractExact(Math.addExact(mealAmount, deliveryFee), discountAmount)) {
                throw new IllegalArgumentException("actualAllocatedAmount must match the order amount composition");
            }
        }
    }
}
