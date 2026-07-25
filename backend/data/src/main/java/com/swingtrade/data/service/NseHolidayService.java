package com.swingtrade.data.service;

import com.swingtrade.data.entity.NseHolidayEntity;
import com.swingtrade.data.repository.NseHolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * Manages NSE holiday calendar.
 * The scheduler uses this to skip ingestion on market holidays.
 */
@Service
public class NseHolidayService {

    private static final Logger logger = LoggerFactory.getLogger(NseHolidayService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final NseHolidayRepository holidayRepository;

    public NseHolidayService(NseHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * Check if a given date is an NSE holiday.
     * Also returns false for Saturdays and Sundays (market closed).
     */
    public boolean isMarketClosed(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }
        return holidayRepository.findByDate(date).isPresent();
    }

    /**
     * Check if today (IST) is a market holiday.
     */
    public boolean isTodayMarketClosed() {
        return isMarketClosed(LocalDate.now(IST));
    }

    /**
     * Get the holiday on a specific date.
     */
    public Optional<NseHolidayEntity> getHoliday(LocalDate date) {
        return holidayRepository.findByDate(date);
    }

    /**
     * Get all upcoming holidays from a given date.
     */
    public List<NseHolidayEntity> getUpcomingHolidays(LocalDate from) {
        return holidayRepository.findUpcomingHolidays(from);
    }

    /**
     * Get all holidays in a date range.
     */
    public List<NseHolidayEntity> getHolidays(LocalDate start, LocalDate end) {
        return holidayRepository.findByDateRange(start, end);
    }

    /**
     * Add a new holiday.
     */
    @Transactional
    public NseHolidayEntity addHoliday(LocalDate date, String occasion, String type) {
        if (holidayRepository.findByDate(date).isPresent()) {
            logger.warn("Holiday already exists on {}: skipping", date);
            return holidayRepository.findByDate(date).get();
        }
        NseHolidayEntity entity = new NseHolidayEntity(date, occasion, type);
        logger.info("Added holiday: {} ({})", date, occasion);
        return holidayRepository.save(entity);
    }

    /**
     * Remove a holiday.
     */
    @Transactional
    public boolean removeHoliday(LocalDate date) {
        Optional<NseHolidayEntity> existing = holidayRepository.findByDate(date);
        if (existing.isPresent()) {
            holidayRepository.delete(existing.get());
            logger.info("Removed holiday: {}", date);
            return true;
        }
        return false;
    }

    /**
     * Count total holidays in the database.
     */
    public long getHolidayCount() {
        return holidayRepository.count();
    }
}