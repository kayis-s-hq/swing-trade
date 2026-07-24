package com.swingtrade.domain.store;

import com.swingtrade.domain.Stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockStore {

    List<Stock> findAllActive();

    Optional<Stock> findBySymbol(String symbol);

    void save(Stock stock);

    boolean existsBySymbol(String symbol);

    List<Stock> findBySector(String sector);

    List<Stock> findByAddedOnBefore(LocalDate addedOn);

    List<Stock> findAllByOrderBySymbol();

    List<String> findAllDistinctSymbols();
}