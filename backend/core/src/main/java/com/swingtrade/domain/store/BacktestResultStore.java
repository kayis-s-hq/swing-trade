package com.swingtrade.domain.store;

import com.swingtrade.domain.BacktestResult;
import java.time.LocalDate;
import java.util.Optional;

public interface BacktestResultStore {
    Optional<BacktestResult> findBySymbolAndDate(String symbol, LocalDate date);
    BacktestResult save(BacktestResult result);
    BacktestResult saveOrUpdate(BacktestResult result);
}
