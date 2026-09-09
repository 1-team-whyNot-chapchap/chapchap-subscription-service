package com.chapchap.subscription.domain.payment.client;

import java.util.Optional;

/**
 * 구독 결제 업무 코드가 특정 외부 결제 제공자의 API 형식에 의존하지 않도록 분리한 자동결제 경계다.
 */
public interface AutomaticPaymentClient {
    /**
     * 요청 시점에 확정한 결제수단 참조값과 금액으로 자동결제를 요청한다.
     *
     * <p>구현체는 제공자 응답을 내부 공통 결과로 변환해야 하며, 결제수단 참조값과
     * API Secret을 로그나 예외 메시지에 노출해서는 안 된다.</p>
     *
     * @param request 외부 요청 식별자·결제수단 참조값·주문명·금액·통화가 확정된 요청
     * @return 외부 제공자 응답을 성공 또는 실패로 변환한 공통 결과
     */
    AutomaticPaymentResult pay(AutomaticPaymentRequest request);

    /**
     * POST 결과가 불명확할 때 같은 외부 결제 식별자의 확정 결과만 단건 조회한다.
     *
     * <p>조회 자체가 실패하거나 응답 상태가 아직 확정되지 않았다면 빈 결과를 반환한다.
     * 호출자는 새 결제를 요청하거나 자동 재시도하지 않고 기존 처리 중 상태를 유지해야 한다.</p>
     *
     * @param externalPaymentId 최초 POST에 사용한 외부 결제 식별자
     * @return PAID 또는 FAILED로 확정된 결과, 그 외에는 빈 결과
     */
    Optional<AutomaticPaymentResult> findConfirmedResult(String externalPaymentId);
}
