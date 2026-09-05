package com.chapchap.subscription.domain.subscription.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/** 상태 변경 없이 결제 담당자에게 넘길 설정 변경 검토 결과다. */
public record SettingChangePreparationResult(
    Long userId,
    Long subscriptionId,
    Long currentSettingId,
    Long requestedPlanId,
    LocalDateTime referenceAt,
    LocalDate effectiveStartDate,
    SettingChangeDraft draft,
    List<Long> replaceableOrderIds
) {
    public SettingChangePreparationResult {
        replaceableOrderIds = List.copyOf(replaceableOrderIds);
    }
}
