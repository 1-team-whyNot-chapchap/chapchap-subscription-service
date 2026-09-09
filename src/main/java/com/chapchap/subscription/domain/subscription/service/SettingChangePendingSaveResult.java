package com.chapchap.subscription.domain.subscription.service;

import java.util.List;

/** 변경 대기 설정과 변경 주문을 같은 로컬 트랜잭션에서 저장한 결과다. */
public record SettingChangePendingSaveResult(Long settingId, int settingSequence, List<Long> orderIds) {
    public SettingChangePendingSaveResult {
        orderIds = List.copyOf(orderIds);
    }
}
