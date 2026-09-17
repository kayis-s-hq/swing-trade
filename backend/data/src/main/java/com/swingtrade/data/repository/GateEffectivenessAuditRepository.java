package com.swingtrade.data.repository;

import com.swingtrade.data.entity.GateEffectivenessAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GateEffectivenessAuditRepository extends JpaRepository<GateEffectivenessAuditEntity, Long> {
    Optional<GateEffectivenessAuditEntity> findBySymbolAndSignalDateAndGateName(
        String symbol, LocalDate signalDate, String gateName);

    List<GateEffectivenessAuditEntity> findByGateNameAndSignalDateBetweenOrderBySignalDateAsc(
        String gateName, LocalDate from, LocalDate to);

    List<GateEffectivenessAuditEntity> findByGateNameAndSymbolAndSignalDateBetweenOrderBySignalDateAsc(
        String gateName, String symbol, LocalDate from, LocalDate to);
}
