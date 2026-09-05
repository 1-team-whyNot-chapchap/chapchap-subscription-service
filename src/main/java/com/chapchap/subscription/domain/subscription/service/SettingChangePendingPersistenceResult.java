package com.chapchap.subscription.domain.subscription.service;

/** 변경 대기 설정과 배송조건 저장이 완료된 뒤 후속 주문 생성 단계에 전달하는 내부 결과다. */
public record SettingChangePendingPersistenceResult(Long settingId, int settingSequence) {
}
