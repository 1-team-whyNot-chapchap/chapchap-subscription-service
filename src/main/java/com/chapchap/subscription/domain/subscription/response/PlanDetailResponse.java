package com.chapchap.subscription.domain.subscription.response;

import java.util.List;

/** 공개 플랜과 1~31번 고정 메뉴 안내 응답이다. */
public record PlanDetailResponse(
    String planId,
    String name,
    String description,
    Long unitPrice,
    List<MenuResponse> menus
) {
    public PlanDetailResponse {
        menus = List.copyOf(menus);
    }

    public record MenuResponse(
        Integer menuSequence,
        String name,
        String description,
        String allergenInfo,
        String nutritionInfo,
        String ingredientInfo
    ) {
    }
}
