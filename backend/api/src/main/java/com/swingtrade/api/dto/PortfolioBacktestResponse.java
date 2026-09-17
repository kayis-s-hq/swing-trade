package com.swingtrade.api.dto;

import com.swingtrade.domain.BenchmarkComparison;
import com.swingtrade.strategy.BacktestTrade;
import com.swingtrade.strategy.PortfolioBacktestResult;
import com.swingtrade.strategy.PortfolioEquityPoint;

import java.time.LocalDate;
import java.util.List;

/** API representation of a shared-capital portfolio backtest result. */
public record PortfolioBacktestResponse(
        LocalDate evaluationStart,
        LocalDate evaluationEnd,
        double initialCapital,
        double finalCapital,
        double totalReturn,
        double maxDrawdownPct,
        double sharpeRatio,
        double cagrPct,
        double sortinoRatio,
        double calmarRatio,
        int totalTrades,
        int winningTrades,
        int rejectedTrades,
        List<PortfolioBacktestTradeResponse> trades,
        List<PortfolioEquityPointResponse> equityCurve,
        BenchmarkComparison benchmarkComparison
) {
    public static PortfolioBacktestResponse from(PortfolioBacktestResult result) {
        return new PortfolioBacktestResponse(result.evaluationStart(), result.evaluationEnd(),
                result.initialCapital(), result.finalCapital(), result.totalReturn(),
                result.maxDrawdownPct(), result.sharpeRatio(), result.cagrPct(),
                result.sortinoRatio(), result.calmarRatio(), result.totalTrades(),
                result.winningTrades(), result.rejectedTrades(),
                result.trades().stream().map(PortfolioBacktestTradeResponse::from).toList(),
                result.equityCurve().stream().map(PortfolioEquityPointResponse::from).toList(),
                result.benchmarkComparison());
    }

    public record PortfolioBacktestTradeResponse(
            String symbol,
            LocalDate entryDate,
            LocalDate exitDate,
            String entryPrice,
            String exitPrice,
            String stopLoss,
            String target,
            int quantity,
            String exitReason,
            double pnl,
            double pnlPct,
            int holdingDays
    ) {
        static PortfolioBacktestTradeResponse from(BacktestTrade trade) {
            return new PortfolioBacktestTradeResponse(trade.symbol(), trade.entryDate(), trade.exitDate(),
                    trade.entryPrice().toPlainString(), trade.exitPrice().toPlainString(),
                    trade.stopLoss().toPlainString(), trade.target().toPlainString(), trade.quantity(),
                    trade.exitReason().name(), trade.pnl(), trade.pnlPct(), trade.holdingDays());
        }
    }

    public record PortfolioEquityPointResponse(
            LocalDate date,
            double equity,
            double settledCash,
            double unsettledProceeds,
            double positionMarketValue
    ) {
        static PortfolioEquityPointResponse from(PortfolioEquityPoint point) {
            return new PortfolioEquityPointResponse(point.date(), point.equity(), point.settledCash(),
                    point.unsettledProceeds(), point.positionMarketValue());
        }
    }
}
