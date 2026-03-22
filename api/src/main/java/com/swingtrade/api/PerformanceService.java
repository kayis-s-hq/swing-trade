package com.swingtrade.api;

import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.broker.engine.PaperTradingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for retrieving performance statistics
 */
@Service
public class PerformanceService {

    private final PaperTradingEngine paperTradingEngine;
    private final PositionRepository positionRepository;

    @Autowired
    public PerformanceService(PaperTradingEngine paperTradingEngine, PositionRepository positionRepository) {
        this.paperTradingEngine = paperTradingEngine;
        this.positionRepository = positionRepository;
    }

    /**
     * Get performance statistics for backtesting and paper trading
     * @return Performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        // Calculate metrics from actual trades and positions
        double totalPnL = calculateTotalPnL();
        double totalReturn = calculateTotalReturn(totalPnL);
        double annualizedReturn = calculateAnnualizedReturn(totalReturn);
        double sharpeRatio = calculateSharpeRatio();
        double maxDrawdown = calculateMaxDrawdown();
        int totalTrades = getTotalTrades();
        int winningTrades = getWinningTrades();
        BigDecimal avgWin = calculateAvgWin();
        BigDecimal avgLoss = calculateAvgLoss();

        return new PerformanceStats(
            BigDecimal.valueOf(totalReturn),
            BigDecimal.valueOf(annualizedReturn),
            BigDecimal.valueOf(sharpeRatio),
            BigDecimal.valueOf(maxDrawdown),
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
    private double calculateTotalPnL() {
        // Sum P&L from all closed positions
        // This would query the trade repository for actual data
        // For now, return calculated value from paper trading engine
        return paperTradingEngine.getTotalPnL();
    }

    /**
     * Calculate total return percentage
     */
    private double calculateTotalReturn(double totalPnL) {
        double initialCapital = 100000.0; // Default initial capital
        return (totalPnL / initialCapital) * 100;
    }

    /**
     * Calculate annualized return
     */
    private double calculateAnnualizedReturn(double totalReturn) {
        // Assuming 1 year of trading data
        // In production, calculate based on actual trading period
        return totalReturn;
    }

    /**
     * Calculate Sharpe ratio
     */
    private double calculateSharpeRatio() {
        // Risk-free rate for Indian government bonds
        double riskFreeRate = 6.0;

        // Calculate from actual trade returns
        // For now, return a placeholder
        return 1.0;
    }

    /**
     * Calculate maximum drawdown
     */
    private double calculateMaxDrawdown() {
        // Calculate from equity curve
        // For now, return a placeholder
        return 5.0;
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
        // Count profitable closed positions
        return (int) positionRepository.findAll().stream()
            .filter(p -> "CLOSED".equals(p.getStatus()) && p.getPnl() > 0)
            .count();
    }

    /**
     * Calculate average win
     */
    private BigDecimal calculateAvgWin() {
        // Calculate average P&L from winning trades
        // For now, return a placeholder
        return BigDecimal.valueOf(500.0);
    }

    /**
     * Calculate average loss
     */
    private BigDecimal calculateAvgLoss() {
        // Calculate average P&L from losing trades
        // For now, return a placeholder
        return BigDecimal.valueOf(300.0);
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
