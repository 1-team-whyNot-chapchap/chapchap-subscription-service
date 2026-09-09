package com.chapchap.subscription.domain.payment.service.command;

/**
 * 준비된 첫 결제 거래를 외부 자동결제로 실행하기 위한 입력이다.
 *
 * @param paymentTransactionId 실행할 처리 중 결제 거래 식별자
 * @param orderName 외부 결제 내역에 표시할 주문명
 */
public record FirstPaymentExecutionCommand(Long paymentTransactionId, String orderName) {
    /** 거래 식별자와 외부 결제 내역에 표시할 주문명을 검증한다. */
    public FirstPaymentExecutionCommand {
        if (paymentTransactionId == null || paymentTransactionId <= 0) {
            throw new IllegalArgumentException("paymentTransactionId must be positive");
        }
        if (orderName == null || orderName.isBlank()) {
            throw new IllegalArgumentException("orderName must not be blank");
        }
    }
}
