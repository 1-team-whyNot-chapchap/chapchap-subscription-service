package com.chapchap.subscription.domain.holiday.controller;

import com.chapchap.subscription.domain.holiday.response.HolidayListResponse;
import com.chapchap.subscription.domain.holiday.service.HolidayQueryService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 메뉴 안내에 사용할 공휴일 기준정보를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription/holidays")
@Tag(name = "공휴일")
public class HolidayController {
    private final HolidayQueryService holidayQueryService;

    @GetMapping
    @Operation(summary = "공개 공휴일 조회", description = "로그인 없이 공휴일 정보 제공 범위와 해당 범위의 공휴일 전체를 날짜 오름차순으로 조회합니다. 제공 범위는 구독 이용 기간이나 배송 가능 기간이 아닙니다.")
    @CustomApiResponse({ErrorCode.DATABASE_ERROR, ErrorCode.INTERNAL_SERVER_ERROR})
    public GlobalResponse<HolidayListResponse> getHolidays() {
        return GlobalResponse.success(holidayQueryService.getHolidays());
    }
}
