package com.swingtrade.data.service;

import com.swingtrade.data.entity.NseHolidayEntity;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** The single source of truth for whether an NSE daily candle may exist. */
@Component
public class MarketCalendar {
    public static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private final NseHolidayService holidayService;
    private final com.swingtrade.data.repository.NseCalendarCoverageRepository coverageRepository;

    public MarketCalendar(NseHolidayService holidayService,
                          com.swingtrade.data.repository.NseCalendarCoverageRepository coverageRepository) {
        this.holidayService = holidayService;
        this.coverageRepository = coverageRepository;
    }

    public boolean isCoverageVerified(LocalDate from, LocalDate to) {
        return IntStream.rangeClosed(from.getYear(), to.getYear()).allMatch(year ->
            coverageRepository.findById(year).map(c -> "VERIFIED".equals(c.getStatus())).orElse(false));
    }

    public boolean isNseTradingSession(LocalDate date) {
        Optional<NseHolidayEntity> holiday = holidayService.getHoliday(date);
        if (holiday.isPresent()) {
            return !"FULL".equalsIgnoreCase(holiday.get().getHolidayType());
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public List<LocalDate> expectedNseSessions(LocalDate from, LocalDate to) {
        List<LocalDate> result = new java.util.ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isNseTradingSession(date)) result.add(date);
        }
        return result;
    }
}
