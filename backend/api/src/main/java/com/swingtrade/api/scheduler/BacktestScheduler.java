package com.swingtrade.api.scheduler;

import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestReportSummary;
import com.swingtrade.strategy.BacktestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Runs a full backtest across the active watchlist weekly and saves the report.
 */
@Component
public class BacktestScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BacktestScheduler.class);

    private final BacktestEngine backtestEngine;

    public BacktestScheduler(BacktestEngine backtestEngine) {
        this.backtestEngine = backtestEngine;
    }

    /**
     * Cron: "0 0 2 * * SUN" = 02:00 IST on Sundays.
     */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "Asia/Kolkata")
    public void runWeeklyBacktest() {
        logger.info("Starting weekly backtest run");

        try {
            List<BacktestResult> results = backtestEngine.runBacktestAll("NSE", BacktestConfig.defaults());
            BacktestReportSummary summary = backtestEngine.generateReport(results);

            logger.info(String.format(Locale.ROOT,
                "Weekly backtest completed: %d symbols, overall win rate %.2f%%, overall Sharpe %.2f",
                summary.symbolsBacktested(), summary.overallWinRate(), summary.overallSharpeRatio()));
        } catch (Exception e) {
            logger.error("Error during weekly backtest run: {}", e.getMessage(), e);
        }
    }
}
