package com.swingtrade.repository;

import com.swingtrade.model.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {
    List<TradeSignal> findByStockId(Long stockId);
    List<TradeSignal> findByStatus(TradeSignal.SignalStatus status);
    List<TradeSignal> findByType(TradeSignal.SignalType type);
}
