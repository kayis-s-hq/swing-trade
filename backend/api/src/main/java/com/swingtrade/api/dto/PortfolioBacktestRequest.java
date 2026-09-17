package com.swingtrade.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Input for the API-only shared-capital portfolio backtest. */
public record PortfolioBacktestRequest(
        List<String> symbols,
        String exchange,
        LocalDate evaluationStart,
        LocalDate evaluationEnd,
        String strategyName,
        PortfolioBacktestConfigRequest config
) {}
