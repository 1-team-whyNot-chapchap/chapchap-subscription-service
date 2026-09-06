package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;

public record PreparedSettingChangeRefund(Long refundId, String refundPublicId,
    RefundStatus status, Long cancellationTransactionId) {}
