package com.swingtrade.data.service;

import com.swingtrade.data.entity.NseHolidayEntity;
import com.swingtrade.data.repository.NseHolidayRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NseHolidayServiceTest {

    @Test
    void reportsWeekendsAndPersistedHolidaysAsClosed() {
        NseHolidayRepository repository = mock(NseHolidayRepository.class);
        LocalDate holiday = LocalDate.of(2026, 1, 26);
        when(repository.findByDate(holiday)).thenReturn(Optional.of(
            new NseHolidayEntity(holiday, "Republic Day", "FULL")));
        NseHolidayService service = new NseHolidayService(repository);

        assertThat(service.isMarketClosed(LocalDate.of(2026, 1, 24))).isTrue();
        assertThat(service.isMarketClosed(holiday)).isTrue();
        assertThat(service.isMarketClosed(LocalDate.of(2026, 1, 27))).isFalse();
    }

    @Test
    void addIsIdempotentAndRemoveReportsWhetherARecordExisted() {
        NseHolidayRepository repository = mock(NseHolidayRepository.class);
        LocalDate date = LocalDate.of(2026, 10, 2);
        NseHolidayEntity existing = new NseHolidayEntity(date, "Gandhi Jayanti", "FULL");
        when(repository.findByDate(date)).thenReturn(Optional.of(existing), Optional.of(existing), Optional.of(existing));
        NseHolidayService service = new NseHolidayService(repository);

        assertThat(service.addHoliday(date, "ignored", "FULL")).isSameAs(existing);
        assertThat(service.removeHoliday(date)).isTrue();
        verify(repository).delete(existing);
    }

    @Test
    void delegatesRangeQueriesAndCreatesNewHoliday() {
        NseHolidayRepository repository = mock(NseHolidayRepository.class);
        LocalDate date = LocalDate.of(2026, 5, 1);
        NseHolidayEntity saved = new NseHolidayEntity(date, "Labour Day", "FULL");
        when(repository.findByDate(date)).thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.save(org.mockito.ArgumentMatchers.any(NseHolidayEntity.class))).thenReturn(saved);
        when(repository.findByDateRange(date, date)).thenReturn(List.of(saved));
        when(repository.findUpcomingHolidays(date)).thenReturn(List.of(saved));
        when(repository.count()).thenReturn(1L);
        NseHolidayService service = new NseHolidayService(repository);

        assertThat(service.addHoliday(date, "Labour Day", "FULL")).isSameAs(saved);
        assertThat(service.getHolidays(date, date)).containsExactly(saved);
        assertThat(service.getUpcomingHolidays(date)).containsExactly(saved);
        assertThat(service.getHolidayCount()).isEqualTo(1L);
        assertThat(service.removeHoliday(date)).isTrue();
    }

    @Test
    void removeReturnsFalseWhenHolidayIsMissing() {
        NseHolidayRepository repository = mock(NseHolidayRepository.class);
        when(repository.findByDate(org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(Optional.empty());

        assertThat(new NseHolidayService(repository).removeHoliday(LocalDate.of(2026, 5, 2))).isFalse();
    }
}
