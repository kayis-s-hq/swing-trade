package com.swingtrade.domain.store;

import com.swingtrade.domain.Trade;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradeStore {

    List<Trade> findBySymbol(String symbol);

    Optional<Trade> findOpenByPositionId(Long positionId);

    List<Trade> findByEntryDateBetween(LocalDate start, LocalDate end);

    List<Trade> findByExitDateBetween(LocalDate start, LocalDate end);

    List<Trade> findByStatus(Trade.TradeStatus status);

    List<Trade> findAllOpen();

    List<Trade> findAllClosed();

    List<Trade> findAll();

    Trade save(Trade trade);

    long countBySymbol(String symbol);

    long countOpenTrades();
}