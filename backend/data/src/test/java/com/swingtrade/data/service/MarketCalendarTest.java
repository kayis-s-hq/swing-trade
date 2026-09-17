package com.swingtrade.data.service;

import com.swingtrade.data.entity.NseCalendarCoverageEntity;
import com.swingtrade.data.entity.NseHolidayEntity;
import com.swingtrade.data.repository.NseCalendarCoverageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketCalendarTest {

    @Test
    void excludesWeekendsAndFullHolidaysButKeepsPartialSessions() {
        NseHolidayService holidays = mock(NseHolidayService.class);
        NseCalendarCoverageRepository coverage = mock(NseCalendarCoverageRepository.class);
        LocalDate fullHoliday = LocalDate.of(2026, 1, 6);
        LocalDate partialHoliday = LocalDate.of(2026, 1, 7);
        when(holidays.getHoliday(fullHoliday)).thenReturn(Optional.of(
            new NseHolidayEntity(fullHoliday, "Full", "FULL")));
        when(holidays.getHoliday(partialHoliday)).thenReturn(Optional.of(
            new NseHolidayEntity(partialHoliday, "Partial", "PARTIAL")));

        MarketCalendar calendar = new MarketCalendar(holidays, coverage);

        assertThat(calendar.isNseTradingSession(LocalDate.of(2026, 1, 3))).isFalse();
        assertThat(calendar.isNseTradingSession(fullHoliday)).isFalse();
        assertThat(calendar.isNseTradingSession(partialHoliday)).isTrue();
        assertThat(calendar.expectedNseSessions(LocalDate.of(2026, 1, 2), partialHoliday))
            .containsExactly(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5), partialHoliday);
    }

    @Test
    void coverageVerificationRequiresEveryYearToBeVerified() {
        NseHolidayService holidays = mock(NseHolidayService.class);
        NseCalendarCoverageRepository coverage = mock(NseCalendarCoverageRepository.class);
        NseCalendarCoverageEntity verified = mock(NseCalendarCoverageEntity.class);
        when(verified.getStatus()).thenReturn("VERIFIED");
        when(coverage.findById(2026)).thenReturn(Optional.of(verified));
        when(coverage.findById(2027)).thenReturn(Optional.empty());
        MarketCalendar calendar = new MarketCalendar(holidays, coverage);

        assertThat(calendar.isCoverageVerified(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(calendar.isCoverageVerified(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).isFalse();
    }
}
