package com.swingtrade.api.service;

import com.swingtrade.domain.store.BacktestResultStore;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;

/** The BACKTEST stage body, extracted from {@link JobOrchestratorService}. */
final class BacktestStage {

    private static final Logger logger = LoggerFactory.getLogger(BacktestStage.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BacktestEngine backtestEngine;
    private final BacktestResultStore backtestResultStore;

    BacktestStage(BacktestEngine backtestEngine, BacktestResultStore backtestResultStore) {
        this.backtestEngine = backtestEngine;
        this.backtestResultStore = backtestResultStore;
    }

    /** Runs the backtest; {@code persist=false} (dry run) skips writing the result row. */
    JobOrchestratorService.StageExecutionResult run(String symbol, boolean persist) {
        boolean save = persist && backtestResultStore != null;
        try {
            BacktestResult result = backtestEngine.runBacktest(symbol, "NSE", BacktestConfig.defaults());
            if (save) {
                backtestResultStore.saveOrUpdate(new com.swingtrade.domain.BacktestResult(null, symbol,
                    LocalDate.now(IST), result.totalTrades(), result.winningTrades(), result.losingTrades(),
                    result.winRate(), result.avgGainPct().doubleValue(), result.avgLossPct().doubleValue(),
                    result.maxDrawdownPct().doubleValue(), result.sharpeRatio(), result.totalReturn().doubleValue(),
                    result.expectancy(), BacktestScorer.calculateProfitFactor(result), true));
            }
            String summary = result.totalTrades() + " trades, "
                + String.format("%.0f", result.winRate()) + "% win, "
                + String.format("%.1f", result.totalReturn()) + "% return";
            return new JobOrchestratorService.StageExecutionResult(
                com.swingtrade.domain.JobRunStage.Status.COMPLETED, summary);
        } catch (IllegalStateException e) {
            logger.info("Backtest skipped for {}: {}", symbol, e.getMessage());
            if (save) {
                backtestResultStore.saveOrUpdate(new com.swingtrade.domain.BacktestResult(null, symbol,
                    LocalDate.now(IST), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false));
            }
            return new JobOrchestratorService.StageExecutionResult(
                com.swingtrade.domain.JobRunStage.Status.SKIPPED, e.getMessage());
        }
    }
}
