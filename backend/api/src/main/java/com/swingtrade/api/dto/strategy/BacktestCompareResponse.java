package com.swingtrade.api.dto.strategy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response body for {@code POST /api/backtest/compare} (plan §6.8). */
public record BacktestCompareResponse(List<VariantResult> variants) {

    public record VariantResult(
        String variantId,
        int version,
        String strategyType,
        List<FoldResult> folds,
        double deflatedSharpeRatio,
        long trialsUsedForDsr,
        boolean walkForwardUnstable,
        Double sharpeStdDevAcrossFolds
    ) {
    }

    /** fold=0 for a plain (non-walk-forward) run; fold=1..N for walk-forward folds. */
    public record FoldResult(
        int fold,
        LocalDate windowStart,
        LocalDate windowEnd,
        Map<String, Object> metrics,
        List<Map<String, Object>> equityCurve
    ) {
    }
}
