package com.swingtrade.data.repository;

import com.swingtrade.data.entity.NseCalendarCoverageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NseCalendarCoverageRepository extends JpaRepository<NseCalendarCoverageEntity, Integer> {
    List<NseCalendarCoverageEntity> findByCalendarYearBetween(int from, int to);
}
