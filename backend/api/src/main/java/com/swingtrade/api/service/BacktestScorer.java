package com.swingtrade.api.service;

import com.swingtrade.data.service.WatchlistService;
import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.strategy.BacktestConfig;
import java.util.Locale;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BacktestScorer {

    private static final Logger logger = LoggerFactory.getLogger(BacktestScorer.class);
    private static final String DEFAULT_EXCHANGE = "NSE";

    private final BacktestEngine backtestEngine;
    private final WatchlistService watchlistService;

    public BacktestScorer(BacktestEngine backtestEngine, WatchlistService watchlistService) {
        this.backtestEngine = backtestEngine;
        this.watchlistService = watchlistService;
    }

    public com.swingtrade.api.dto.CompositeAnalysis.BacktestScore compute(String symbol) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        String exchange = watchlistService.getBySymbol(sym)
            .map(WatchlistEntity::getExchange)
            .orElse(DEFAULT_EXCHANGE);

        try {
            BacktestResult result = backtestEngine.runBacktest(sym, exchange, BacktestConfig.defaults());
            return new com.swingtrade.api.dto.CompositeAnalysis.BacktestScore(
                result.totalTrades(),
                result.winRate(),
                calculateProfitFactor(result),
                result.maxDrawdownPct(),
                result.totalReturn(),
                result.expectancy(),
                result.totalTrades() > 0
            );
        } catch (IllegalStateException e) {
            logger.warn("Insufficient data for backtest: {}", sym);
            return new com.swingtrade.api.dto.CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false);
        } catch (Exception e) {
            logger.warn("Backtest failed for {}: {}", sym, e.getMessage());
            return new com.swingtrade.api.dto.CompositeAnalysis.BacktestScore(0, 0, 0, 0, 0, 0, false);
        }
    }

    /**
     * Calculate profit factor from backtest result.
     * profitFactor = abs(winning trades' total pnl) / abs(losing trades' total pnl)
     */
    private double calculateProfitFactor(BacktestResult result) {
        if (result.trades() == null || result.trades().isEmpty()) {
            return 0;
        }

        double totalWin = 0;
        double totalLoss = 0;

        for (var trade : result.trades()) {
            if (trade.pnl() > 0) {
                totalWin += trade.pnl();
            } else if (trade.pnl() < 0) {
                totalLoss += Math.abs(trade.pnl());
            }
        }

        if (totalLoss == 0) {
            return totalWin > 0 ? 100 : 0; // All wins or no trades
        }

        return totalWin / totalLoss;
    }
}