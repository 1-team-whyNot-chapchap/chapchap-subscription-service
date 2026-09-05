package com.chapchap.subscription.domain.subscription.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 결제·환불 확정 전 설정 변경 사전 생성 결과다. */
public record PreparedSettingChange(
    Long subscriptionId,
    Long settingId,
    int settingSequence,
    LocalDateTime referenceAt,
    LocalDate effectiveStartDate,
    int createdOrderCount
) {
}
