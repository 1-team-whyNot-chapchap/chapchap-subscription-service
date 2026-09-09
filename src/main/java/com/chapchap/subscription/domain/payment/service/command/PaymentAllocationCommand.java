package com.chapchap.subscription.domain.payment.service.command;

/**
 * 성공한 첫 결제금액 중 주문 한 건에 배분할 금액이다.
 *
 * @param orderId 배분 대상 주문의 내부 식별자
 * @param allocationAmount 해당 주문에 배분할 실제 결제금액
 */
public record PaymentAllocationCommand(Long orderId, Long allocationAmount) {
    /** 주문 식별자와 배분금액이 양수인지 확인한다. */
    public PaymentAllocationCommand {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (allocationAmount == null || allocationAmount <= 0) {
            throw new IllegalArgumentException("allocationAmount must be positive");
        }
    }
}
