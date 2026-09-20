package com.swingtrade.domain.store;

import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionSummary;

import java.util.List;
import java.util.Optional;

public interface PositionStore {

    List<Position> findAllOpen();

    /** Open positions as lightweight summaries, for callers that do not need the full aggregate. */
    List<PositionSummary> findOpenSummaries();

    List<Position> findAll();

    Optional<Position> findById(Long id);

    Optional<Position> findBySymbol(String symbol);

    List<Position> findBySymbolOrderByEntryDateDesc(String symbol);

    List<Position> findByStatus(com.swingtrade.domain.PositionStatus status);

    Position save(Position position);

    boolean existsOpenBySymbol(String symbol);

    List<Position> findByBrokerType(String brokerType);
}