package com.swingtrade.api.service;

import com.swingtrade.api.dto.PortfolioBacktestRequest;
import com.swingtrade.api.dto.PortfolioBacktestResponse;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.PortfolioBacktestResult;
import com.swingtrade.strategy.StrategyRegistry;
import com.swingtrade.strategy.TradingStrategy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PortfolioBacktestService {
    private final BacktestEngine backtestEngine;
    private final StrategyRegistry strategyRegistry;

    public PortfolioBacktestService(BacktestEngine backtestEngine, StrategyRegistry strategyRegistry) {
        this.backtestEngine = backtestEngine;
        this.strategyRegistry = strategyRegistry;
    }

    public PortfolioBacktestResponse run(PortfolioBacktestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        List<String> symbols = request.symbols() == null ? List.of() : request.symbols().stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }
        LocalDate start = request.evaluationStart();
        LocalDate end = request.evaluationEnd();
        if (start == null || end == null) {
            throw new IllegalArgumentException("evaluationStart and evaluationEnd are required");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("evaluationStart must not be after evaluationEnd");
        }
        String exchange = request.exchange() == null || request.exchange().isBlank()
                ? "NSE" : request.exchange().trim().toUpperCase();
        TradingStrategy strategy = resolveStrategy(request.strategyName());
        BacktestConfig config = request.config() == null
                ? BacktestConfig.defaults() : request.config().toBacktestConfig();
        PortfolioBacktestResult result = backtestEngine.runPortfolioBacktest(
                symbols, exchange, config, strategy, start, end);
        return PortfolioBacktestResponse.from(result);
    }

    private TradingStrategy resolveStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return strategyRegistry.defaultStrategy();
        }
        return strategyRegistry.find(strategyName.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + strategyName));
    }
}
