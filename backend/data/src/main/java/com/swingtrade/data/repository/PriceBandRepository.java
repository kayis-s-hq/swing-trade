package com.swingtrade.data.repository;

import com.swingtrade.data.entity.PriceBandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PriceBandRepository extends JpaRepository<PriceBandEntity, Long> {
    Optional<PriceBandEntity> findBySymbolAndDate(String symbol, LocalDate date);
}
