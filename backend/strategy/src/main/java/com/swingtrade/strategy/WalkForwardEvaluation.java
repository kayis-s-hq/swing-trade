package com.swingtrade.strategy;

import java.time.LocalDate;
import java.util.List;

/**
 * Bounded walk-forward evaluation made up of independent, chronological OOS folds.
 * The existing single-window backtest API remains the primitive used for each fold.
 */
public record WalkForwardEvaluation(
        List<Fold> folds,
        double averageWinRate,
        double averageTotalReturn,
        int totalTrades
) {
    public WalkForwardEvaluation {
        folds = List.copyOf(folds);
    }

    public record Fold(LocalDate startDate, LocalDate endDate, BacktestResult result) {
    }

    public boolean isComplete(int expectedFolds) {
        return folds.size() == expectedFolds;
    }
}
