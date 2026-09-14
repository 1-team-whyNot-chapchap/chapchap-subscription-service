package com.chapchap.subscription.domain.holiday.service;

import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HolidayQueryServiceTest {
    private final HolidayRepository repository = mock(HolidayRepository.class);
    private final HolidayQueryService service = new HolidayQueryService(repository);
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = LocalDate.of(2027, 12, 31);

    @Test
    void 범위_양끝을_조회하고_공휴일을_정렬하며_필요한_값만_변환한다() {
        when(repository.findAllByHolidayDateBetween(start, end)).thenReturn(List.of(
            holiday(end, "종료 경계 테스트", false),
            holiday(LocalDate.of(2026, 3, 2), "대체공휴일(3·1절)", true),
            holiday(start, "1월 1일", false)
        ));

        var response = service.getHolidays();

        assertThat(response.supportedStartDate()).isEqualTo(start);
        assertThat(response.supportedEndDate()).isEqualTo(end);
        assertThat(response.holidays()).extracting(item -> item.holidayDate())
            .containsExactly(start, LocalDate.of(2026, 3, 2), end);
        assertThat(response.holidays().get(1).holidayName()).isEqualTo("대체공휴일(3·1절)");
        assertThat(response.holidays().get(1).substituteHoliday()).isTrue();
        assertThat(response.holidays().get(0).substituteHoliday()).isFalse();
        verify(repository).findAllByHolidayDateBetween(start, end);
        verifyNoMoreInteractions(repository);
        assertThatThrownBy(() -> response.holidays().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 첫마지막_공휴일과_무관하게_제공_범위를_반환한다() {
        when(repository.findAllByHolidayDateBetween(start, end)).thenReturn(List.of(
            holiday(LocalDate.of(2026, 3, 1), "3·1절", false),
            holiday(LocalDate.of(2027, 10, 9), "한글날", false)
        ));
        var response = service.getHolidays();
        assertThat(response.supportedStartDate()).isEqualTo(start);
        assertThat(response.supportedEndDate()).isEqualTo(end);
    }

    @Test
    void 빈_목록을_정상_공휴일_없음으로_반환하지_않는다() {
        assertInvalid(List.of());
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void 한_연도만_준비되면_오류로_처리한다(int year) {
        assertInvalid(List.of(holiday(LocalDate.of(year, 1, 1), "1월 1일", false)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 공휴일_이름_누락을_거절한다(String name) {
        assertInvalid(List.of(holiday(start, name, false), holiday(end, "경계", false)));
    }

    @Test
    void 공휴일_이름_최대_길이를_검증한다() {
        assertInvalid(List.of(holiday(start, "가".repeat(101), false), holiday(end, "경계", false)));
    }

    @Test
    void 공휴일_날짜_누락을_거절한다() {
        assertInvalid(List.of(holiday(null, "이름", false), holiday(end, "경계", false)));
    }

    @Test
    void 같은_날짜의_중복_응답을_방지한다() {
        assertInvalid(List.of(holiday(start, "이름", false), holiday(start, "중복", false), holiday(end, "경계", false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-12-31", "2028-01-01"})
    void Repository_계약과_다른_범위밖_데이터를_노출하지_않는다(String date) {
        assertInvalid(List.of(holiday(start, "이름", false), holiday(end, "경계", false),
            holiday(LocalDate.parse(date), "범위 밖", false)));
    }

    @Test
    void DB_오류를_빈_목록으로_바꾸지_않는다() {
        var failure = new DataAccessResourceFailureException("test database unavailable");
        when(repository.findAllByHolidayDateBetween(start, end)).thenThrow(failure);
        assertThatThrownBy(service::getHolidays).isSameAs(failure);
    }

    private void assertInvalid(List<Holiday> holidays) {
        when(repository.findAllByHolidayDateBetween(start, end)).thenReturn(holidays);
        assertThatThrownBy(service::getHolidays).isInstanceOf(IllegalStateException.class);
        verify(repository).findAllByHolidayDateBetween(start, end);
        verifyNoMoreInteractions(repository);
    }

    private Holiday holiday(LocalDate date, String name, boolean substitute) {
        Holiday holiday = org.springframework.beans.BeanUtils.instantiateClass(Holiday.class);
        ReflectionTestUtils.setField(holiday, "holidayDate", date);
        ReflectionTestUtils.setField(holiday, "holidayName", name);
        ReflectionTestUtils.setField(holiday, "substituteHoliday", substitute);
        return holiday;
    }
}
