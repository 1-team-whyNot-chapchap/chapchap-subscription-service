package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.response.PlanDetailResponse;
import com.chapchap.subscription.domain.subscription.response.PlanListResponse;
import com.chapchap.subscription.domain.subscription.service.PlanQueryService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전 고객에게 공개 플랜과 고정 메뉴 기준정보를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/plans")
@Tag(name = "플랜")
public class PlanController {
    private final PlanQueryService planQueryService;

    @GetMapping
    @Operation(summary = "플랜 목록 조회", description = "로그인 없이 선택 가능한 구독 플랜의 기준 정보를 조회합니다.")
    @CustomApiResponse({
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<PlanListResponse> getPlans() {
        return GlobalResponse.success(planQueryService.getPlans());
    }

    @GetMapping("/{planId}")
    @Operation(summary = "플랜 상세 조회", description = "플랜과 해당 플랜의 월별 고정 메뉴 정보를 조회합니다.")
    @CustomApiResponse({
        ErrorCode.PLAN_NOT_FOUND,
        ErrorCode.DATABASE_ERROR,
        ErrorCode.INTERNAL_SERVER_ERROR
    })
    public GlobalResponse<PlanDetailResponse> getPlan(
        @Parameter(description = "조회할 플랜의 공개 UUID", required = true, schema = @Schema(format = "uuid"), example = "550e8400-e29b-41d4-a716-446655440000")
        @PathVariable String planId
    ) {
        return GlobalResponse.success(planQueryService.getPlan(planId));
    }
}
