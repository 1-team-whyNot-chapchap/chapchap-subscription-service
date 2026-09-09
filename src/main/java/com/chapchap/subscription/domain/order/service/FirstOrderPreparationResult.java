package com.chapchap.subscription.domain.order.service;

import java.util.List;

/**
 * 저장된 첫 주문별 결제 배분금액과 전체 결제금액을 제공한다.
 *
 * @param orders Payment 배분으로 연결할 주문별 식별자와 실제 배분금액
 * @param totalPaymentAmount 첫 이용 기간의 전체 결제금액
 * @param firstDiscountApplied 주문 금액 계산에 첫 구독 할인을 적용했는지 여부
 */
public record FirstOrderPreparationResult(
    List<OrderAmount> orders,
    long totalPaymentAmount,
    boolean firstDiscountApplied
) {
    /** 주문별 결과와 전체 결제금액을 검증하고 변경 불가능한 목록으로 보존한다. */
    public FirstOrderPreparationResult {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("orders must not be empty");
        }
        orders = List.copyOf(orders);
        if (totalPaymentAmount <= 0) {
            throw new IllegalArgumentException("totalPaymentAmount must be positive");
        }
        long calculatedTotal = 0L;
        for (OrderAmount order : orders) {
            calculatedTotal = Math.addExact(calculatedTotal, order.actualAllocatedAmount());
        }
        if (calculatedTotal != totalPaymentAmount) {
            throw new IllegalArgumentException("totalPaymentAmount must equal the sum of order amounts");
        }
    }

    /**
     * Payment 배분 입력으로 변환할 저장 주문 ID와 실제 배분금액이다.
     *
     * @param orderId 저장된 주문의 내부 식별자
     * @param actualAllocatedAmount 해당 주문에 배분할 실제 결제금액
     */
    public record OrderAmount(Long orderId, long actualAllocatedAmount) {
        /** 주문 식별자와 실제 배분금액이 양수인지 검증한다. */
        public OrderAmount {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("orderId must be positive");
            }
            if (actualAllocatedAmount <= 0) {
                throw new IllegalArgumentException("actualAllocatedAmount must be positive");
            }
        }
    }
}
