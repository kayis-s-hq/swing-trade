package com.swingtrade.data.repository;

import com.swingtrade.data.entity.CorporateActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CorporateActionRepository extends JpaRepository<CorporateActionEntity, Long> {
    List<CorporateActionEntity> findBySymbolAndEffectiveDateBetweenOrderByEffectiveDateAsc(String symbol, LocalDate from, LocalDate to);
    Optional<CorporateActionEntity> findBySymbolAndEffectiveDateAndActionType(String symbol, LocalDate date, String actionType);
}
