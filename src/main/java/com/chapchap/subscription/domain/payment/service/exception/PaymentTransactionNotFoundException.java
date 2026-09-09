package com.chapchap.subscription.domain.payment.service.exception;

/** 내부 첫 결제 단계 사이에 전달된 결제 거래를 찾지 못한 경우 발생한다. */
public class PaymentTransactionNotFoundException extends RuntimeException {
    /** 내부 단계에서 전달된 식별자로 결제 거래를 찾지 못한 예외를 생성한다. */
    public PaymentTransactionNotFoundException() {
        super("Payment transaction was not found");
    }
}
