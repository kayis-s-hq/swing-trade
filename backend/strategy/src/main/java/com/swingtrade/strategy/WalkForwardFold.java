package com.swingtrade.strategy;

import java.time.LocalDate;

/** One rolling out-of-sample fold's window and result (plan §6.3). */
public record WalkForwardFold(LocalDate testStart, LocalDate testEnd, PortfolioBacktestResult result) {
}
