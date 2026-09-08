package com.chapchap.subscription.domain.order.service;

/** 첫 구독 할인 금액을 주문 생성 흐름에서 일관되게 계산한다. */
final class FirstSubscriptionDiscountCalculator {
    private static final long FIRST_DISCOUNT_RATE = 30L;
    private static final long PERCENT_DENOMINATOR = 100L;

    private FirstSubscriptionDiscountCalculator() {
    }

    static long calculate(long mealUnitPrice) {
        long discountNumerator = Math.multiplyExact(mealUnitPrice, FIRST_DISCOUNT_RATE);
        if (discountNumerator % PERCENT_DENOMINATOR != 0) {
            throw new IllegalArgumentException("First discount must be an exact amount in won");
        }
        return discountNumerator / PERCENT_DENOMINATOR;
    }
}
