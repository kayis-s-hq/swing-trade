package com.swingtrade.data.repository;

import com.swingtrade.data.entity.NseHolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NseHolidayRepository extends JpaRepository<NseHolidayEntity, Long> {

    @Query("SELECT h FROM NseHolidayEntity h WHERE h.holidayDate = :date")
    Optional<NseHolidayEntity> findByDate(@Param("date") LocalDate date);

    @Query("SELECT h FROM NseHolidayEntity h WHERE h.holidayDate BETWEEN :start AND :end ORDER BY h.holidayDate")
    List<NseHolidayEntity> findByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT h FROM NseHolidayEntity h WHERE h.holidayDate >= :fromDate ORDER BY h.holidayDate")
    List<NseHolidayEntity> findUpcomingHolidays(@Param("fromDate") LocalDate fromDate);

    long countByHolidayDateBefore(LocalDate date);
}