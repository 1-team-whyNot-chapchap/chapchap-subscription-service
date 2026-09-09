package com.chapchap.subscription.domain.subscription.response;

import java.util.List;

/** 공개 플랜 목록 응답이다. */
public record PlanListResponse(List<PlanItemResponse> plans) {
    public PlanListResponse {
        plans = List.copyOf(plans);
    }

    public record PlanItemResponse(
        String planId,
        String name,
        String description,
        Long unitPrice
    ) {
    }
}
