package com.swingtrade.api.service;

import com.swingtrade.api.dto.PerformanceResponse;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.TradeEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.data.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for retrieving performance statistics
 */
@Service
public class PerformanceService {

    private final PaperTradingEngine paperTradingEngine;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;

    @Autowired
    public PerformanceService(PaperTradingEngine paperTradingEngine, PositionRepository positionRepository, TradeRepository tradeRepository) {
        this.paperTradingEngine = paperTradingEngine;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Get portfolio performance as PerformanceResponse DTO
     * @return PerformanceResponse with real metrics
     */
    public PerformanceResponse getPortfolioPerformance() {
        PerformanceStats stats = getPerformanceStats();
        return PerformanceResponse.of(
            stats.getTotalReturn(),
            stats.getAnnualizedReturn(),
            stats.getSharpeRatio(),
            stats.getMaxDrawdown(),
            stats.getTotalTrades(),
            stats.getWinningTrades()
        );
    }

    /**
     * Get performance statistics for backtesting and paper trading
     * @return Performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        // Calculate metrics from actual trades and positions
        BigDecimal totalPnL = calculateTotalPnL();
        BigDecimal totalReturn = calculateTotalReturn(totalPnL);
        BigDecimal annualizedReturn = calculateAnnualizedReturn(totalReturn);
        BigDecimal sharpeRatio = calculateSharpeRatio();
        BigDecimal maxDrawdown = calculateMaxDrawdown();
        int totalTrades = getTotalTrades();
        int winningTrades = getWinningTrades();
        BigDecimal avgWin = calculateAvgWin();
        BigDecimal avgLoss = calculateAvgLoss();

        return new PerformanceStats(
            totalReturn,
            annualizedReturn,
            sharpeRatio,
            maxDrawdown,
            totalTrades,
            winningTrades,
            avgWin,
            avgLoss,
            LocalDateTime.now()
        );
    }

    /**
     * Calculate total P&L from all closed positions
     */
    private BigDecimal calculateTotalPnL() {
        // Sum P&L from all closed positions
        // This would query the trade repository for actual data
        // For now, return calculated value from paper trading engine
        return paperTradingEngine.getTotalPnL();
    }

    /**
     * Calculate total return percentage
     */
    private BigDecimal calculateTotalReturn(BigDecimal totalPnL) {
        BigDecimal initialCapital = BigDecimal.valueOf(100000); // Default initial capital
        return totalPnL.divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate annualized return
     */
    private BigDecimal calculateAnnualizedReturn(BigDecimal totalReturn) {
        // Assuming 1 year of trading data
        // In production, calculate based on actual trading period
        return totalReturn;
    }

    /**
     * Calculate Sharpe ratio
     */
    private BigDecimal calculateSharpeRatio() {
        List<TradeEntity> closed = tradeRepository.findAllClosedTrades();
        if (closed.size() < 2) return BigDecimal.ZERO;

        BigDecimal riskFreeDaily = BigDecimal.valueOf(0.0004); // ~10% annual / 252
        List<BigDecimal> returns = new java.util.ArrayList<>();
        BigDecimal initialCapital = BigDecimal.valueOf(100000);

        for (TradeEntity t : closed) {
            BigDecimal pnl = t.getTotalPnL() != null ? t.getTotalPnL() : BigDecimal.ZERO;
            returns.add(pnl.divide(initialCapital, 6, RoundingMode.HALF_UP));
        }

        int n = returns.size();
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        double variance = returns.stream()
            .map(r -> r.subtract(mean).pow(2).doubleValue())
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev < 1e-10) return BigDecimal.ZERO;
        double sharpe = (mean.doubleValue() - riskFreeDaily.doubleValue()) / stdDev;
        return BigDecimal.valueOf(sharpe).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate maximum drawdown from equity curve
     */
    private BigDecimal calculateMaxDrawdown() {
        List<TradeEntity> closed = tradeRepository.findAllClosedTrades()
            .stream()
            .sorted(java.util.Comparator.comparing(TradeEntity::getExitDate))
            .toList();

        if (closed.isEmpty()) return BigDecimal.ZERO;

        BigDecimal initialCapital = BigDecimal.valueOf(100000);
        double peak = initialCapital.doubleValue();
        double maxDD = 0.0;
        double equity = initialCapital.doubleValue();

        for (TradeEntity t : closed) {
            BigDecimal pnl = t.getTotalPnL() != null ? t.getTotalPnL() : BigDecimal.ZERO;
            equity += pnl.doubleValue();
            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak;
            if (dd > maxDD) maxDD = dd;
        }

        return BigDecimal.valueOf(maxDD * 100).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get total number of trades
     */
    private int getTotalTrades() {
        // Count closed positions
        return (int) positionRepository.findAll().stream()
            .filter(p -> "CLOSED".equals(p.getStatus()))
            .count();
    }

    /**
     * Get number of winning trades
     */
    private int getWinningTrades() {
        // Count profitable closed positions (current price > entry price)
        return (int) positionRepository.findAll().stream()
            .filter(p -> "CLOSED".equals(p.getStatus())
                && p.getCurrentPrice() != null
                && p.getEntryPrice() != null
                && p.getCurrentPrice().compareTo(p.getEntryPrice()) > 0)
            .count();
    }

    /**
     * Calculate average win
     */
    private BigDecimal calculateAvgWin() {
        List<TradeEntity> closed = tradeRepository.findAllClosedTrades();
        List<BigDecimal> wins = closed.stream()
            .filter(t -> t.getTotalPnL() != null && t.getTotalPnL().compareTo(BigDecimal.ZERO) > 0)
            .map(TradeEntity::getTotalPnL)
            .toList();

        if (wins.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = wins.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(wins.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate average loss
     */
    private BigDecimal calculateAvgLoss() {
        List<TradeEntity> closed = tradeRepository.findAllClosedTrades();
        List<BigDecimal> losses = closed.stream()
            .filter(t -> t.getTotalPnL() != null && t.getTotalPnL().compareTo(BigDecimal.ZERO) < 0)
            .map(TradeEntity::getTotalPnL)
            .map(p -> p.abs())
            .toList();

        if (losses.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(losses.size()), 2, RoundingMode.HALF_UP);
    }

    // DTO class for API response

    public static class PerformanceStats {
        private final BigDecimal totalReturn;
        private final BigDecimal annualizedReturn;
        private final BigDecimal sharpeRatio;
        private final BigDecimal maxDrawdown;
        private final int totalTrades;
        private final int winningTrades;
        private final BigDecimal avgWin;
        private final BigDecimal avgLoss;
        private final LocalDateTime asOfDate;

        public PerformanceStats(BigDecimal totalReturn, BigDecimal annualizedReturn,
                                BigDecimal sharpeRatio, BigDecimal maxDrawdown,
                                int totalTrades, int winningTrades,
                                BigDecimal avgWin, BigDecimal avgLoss,
                                LocalDateTime asOfDate) {
            this.totalReturn = totalReturn;
            this.annualizedReturn = annualizedReturn;
            this.sharpeRatio = sharpeRatio;
            this.maxDrawdown = maxDrawdown;
            this.totalTrades = totalTrades;
            this.winningTrades = winningTrades;
            this.avgWin = avgWin;
            this.avgLoss = avgLoss;
            this.asOfDate = asOfDate;
        }

        public BigDecimal getTotalReturn() { return totalReturn; }
        public BigDecimal getAnnualizedReturn() { return annualizedReturn; }
        public BigDecimal getSharpeRatio() { return sharpeRatio; }
        public BigDecimal getMaxDrawdown() { return maxDrawdown; }
        public int getTotalTrades() { return totalTrades; }
        public int getWinningTrades() { return winningTrades; }
        public BigDecimal getAvgWin() { return avgWin; }
        public BigDecimal getAvgLoss() { return avgLoss; }
        public LocalDateTime getAsOfDate() { return asOfDate; }
    }
}
