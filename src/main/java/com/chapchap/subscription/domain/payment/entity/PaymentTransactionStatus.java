package com.chapchap.subscription.domain.payment.entity;

/** 외부 요청의 진행 여부와 최종 결과를 나타내는 결제 거래 상태다. */
public enum PaymentTransactionStatus {
    /** 외부 요청을 시작했지만 성공 또는 실패 응답을 아직 확정하지 못했다. */
    PROCESSING,
    /** 외부 결제 또는 취소가 성공했다. */
    SUCCESS,
    /** 재시도 없이 실패했거나 허용된 마지막 재시도가 실패했다. */
    FAILED,
    /** 오전 정기결제 실패 후 같은 날 재시도를 기다린다. */
    RETRY_WAITING,
    /** 정기결제 재시도 전에 구독 해지가 확정되어 재시도를 중단했다. */
    RETRY_STOPPED
}
