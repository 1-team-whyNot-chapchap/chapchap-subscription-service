package com.chapchap.subscription.domain.holiday.controller;

import com.chapchap.subscription.domain.holiday.response.HolidayListResponse;
import com.chapchap.subscription.domain.holiday.service.HolidayQueryService;
import com.chapchap.subscription.global.exception.GlobalExceptionHandler;
import com.chapchap.subscription.global.security.filter.HeaderAuthenticationFilter;
import com.chapchap.subscription.global.security.filter.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HolidayController.class)
@Import({SecurityConfiguration.class, HeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class HolidayControllerTest {
    private static final String PATH = "/api/subscription/holidays";
    @Autowired private MockMvc mockMvc;
    @MockitoBean private HolidayQueryService service;

    @Test
    void 인증_헤더_없이_공개_공휴일을_조회하고_계약_필드만_반환한다() throws Exception {
        when(service.getHolidays()).thenReturn(new HolidayListResponse(
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31),
            List.of(new HolidayListResponse.HolidayItemResponse(LocalDate.of(2026, 3, 2), "대체공휴일(3·1절)", true))
        ));
        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(3)))
            .andExpect(jsonPath("$.code").value("00"))
            .andExpect(jsonPath("$.message").value("SUCCESS"))
            .andExpect(jsonPath("$.data", aMapWithSize(3)))
            .andExpect(jsonPath("$.data.supportedStartDate").value("2026-01-01"))
            .andExpect(jsonPath("$.data.supportedEndDate").value("2027-12-31"))
            .andExpect(jsonPath("$.data.holidays[0]", aMapWithSize(3)))
            .andExpect(jsonPath("$.data.holidays[0].holidayDate").value("2026-03-02"))
            .andExpect(jsonPath("$.data.holidays[0].holidayName").value("대체공휴일(3·1절)"))
            .andExpect(jsonPath("$.data.holidays[0].substituteHoliday").value(true));
        verify(service).getHolidays();
        verifyNoMoreInteractions(service);
    }

    @Test
    void 미준비_데이터는_COMMON_099로_응답한다() throws Exception {
        when(service.getHolidays()).thenThrow(new IllegalStateException("test data not ready"));
        mockMvc.perform(get(PATH))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON_099"));
    }

    @Test
    void DB_오류는_COMMON_098로_응답한다() throws Exception {
        when(service.getHolidays()).thenThrow(new DataAccessResourceFailureException("test database unavailable"));
        mockMvc.perform(get(PATH))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON_098"));
    }
}
