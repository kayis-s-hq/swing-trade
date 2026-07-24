package com.swingtrade.domain.store;

import com.swingtrade.domain.Position;

import java.util.List;
import java.util.Optional;

public interface PositionStore {

    List<Position> findAllOpen();

    List<Position> findAll();

    Optional<Position> findById(Long id);

    Optional<Position> findBySymbol(String symbol);

    List<Position> findBySymbolOrderByEntryDateDesc(String symbol);

    List<Position> findByStatus(Position.PositionStatus status);

    Position save(Position position);

    boolean existsOpenBySymbol(String symbol);
}