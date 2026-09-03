package com.swingtrade.api.service;

import com.swingtrade.api.dto.PerformanceResponse;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for retrieving performance statistics from paper trading engine.
 * Reads from unified positions table (broker_type='PAPER').
 */
@Service
public class PerformanceService {

    private static final String BROKER_TYPE_PAPER = "PAPER";

    private final TradingService tradingService;
    private final PositionRepository positionRepo;

    public PerformanceService(TradingService tradingService,
                              PositionRepository positionRepo) {
        this.tradingService = tradingService;
        this.positionRepo = positionRepo;
    }

    /**
     * Get portfolio performance as PerformanceResponse DTO
     * @return PerformanceResponse with real metrics
     */
    public PerformanceResponse getPortfolioPerformance() {
        PerformanceStats stats = getPerformanceStats();
        BigDecimal totalPnL = calculateTotalPnL();
        BigDecimal totalValue = calculateTotalValue();
        List<PositionEntity> closedPositions = fetchClosedPositions();
        BigDecimal avgWin = calculateAvgWin(closedPositions);
        BigDecimal avgLoss = calculateAvgLoss(closedPositions);
        BigDecimal profitFactor = calculateProfitFactor(closedPositions);

        return PerformanceResponse.of(
            stats.getTotalReturn(),
            stats.getAnnualizedReturn(),
            stats.getSharpeRatio(),
            stats.getMaxDrawdown(),
            stats.getTotalTrades(),
            stats.getWinningTrades(),
            totalPnL,
            totalValue,
            avgWin,
            avgLoss,
            profitFactor
        );
    }

    /**
     * Get performance statistics for backtesting and paper trading
     * @return Performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        List<PositionEntity> closed = fetchClosedPositions();
        BigDecimal totalPnL = calculateTotalPnL();
        BigDecimal totalReturn = calculateTotalReturn(totalPnL);
        BigDecimal annualizedReturn = calculateAnnualizedReturn(totalReturn);
        BigDecimal sharpeRatio = calculateSharpeRatio(closed);
        BigDecimal maxDrawdown = calculateMaxDrawdown(closed);
        int totalTrades = getTotalTrades(closed);
        int winningTrades = getWinningTrades(closed);
        BigDecimal avgWin = calculateAvgWin(closed);
        BigDecimal avgLoss = calculateAvgLoss(closed);

        return new PerformanceStats(
            totalReturn, annualizedReturn, sharpeRatio, maxDrawdown,
            totalTrades, winningTrades, avgWin, avgLoss, LocalDateTime.now()
        );
    }

    private List<PositionEntity> fetchClosedPositions() {
        List<PositionEntity> c = new ArrayList<>();
        c.addAll(positionRepo.findByStatus("CLOSED").stream()
            .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
        c.addAll(positionRepo.findByStatus("STOPPED").stream()
            .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
        c.addAll(positionRepo.findByStatus("TARGET_HIT").stream()
            .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
        c.sort(Comparator.comparing(e -> e.getExitTime() != null ? e.getExitTime() : LocalDateTime.MAX));
        return c;
    }

    private BigDecimal calculateTotalPnL() {
        return tradingService.getTotalPnL();
    }

    private BigDecimal calculateTotalReturn(BigDecimal totalPnL) {
        BigDecimal initialCapital = tradingService.getInitialCapital();
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return totalPnL.divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateAnnualizedReturn(BigDecimal totalReturn) {
        return totalReturn;
    }

    private BigDecimal calculateSharpeRatio(List<PositionEntity> closed) {
        if (closed.size() < 2) return BigDecimal.ZERO;

        BigDecimal riskFreeDaily = BigDecimal.valueOf(0.0004);
        BigDecimal initialCapital = tradingService.getInitialCapital();
        List<BigDecimal> returns = new ArrayList<>();

        for (PositionEntity e : closed) {
            BigDecimal pnl = e.getRealizedPnL() != null ? e.getRealizedPnL() : BigDecimal.ZERO;
            returns.add(pnl.divide(initialCapital, 6, RoundingMode.HALF_UP));
        }

        int n = returns.size();
        BigDecimal mean = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        double variance = returns.stream()
            .map(r -> r.subtract(mean).pow(2).doubleValue())
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev < 1e-10) return BigDecimal.ZERO;
        double sharpe = (mean.doubleValue() - riskFreeDaily.doubleValue()) / stdDev;
        return BigDecimal.valueOf(sharpe).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxDrawdown(List<PositionEntity> closed) {
        BigDecimal portfolioDrawdown = tradingService.getPortfolioMaxDrawdown();
        if (portfolioDrawdown != null) return portfolioDrawdown;
        if (closed.isEmpty()) return BigDecimal.ZERO;

        BigDecimal initialCapital = tradingService.getInitialCapital();
        double peak = initialCapital.doubleValue();
        double maxDD = 0.0;
        double equity = initialCapital.doubleValue();

        for (PositionEntity e : closed) {
            BigDecimal pnl = e.getRealizedPnL() != null ? e.getRealizedPnL() : BigDecimal.ZERO;
            equity += pnl.doubleValue();
            if (equity > peak) peak = equity;
            double dd = (peak - equity) / peak;
            if (dd > maxDD) maxDD = dd;
        }

        return BigDecimal.valueOf(maxDD * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private int getTotalTrades(List<PositionEntity> closed) {
        return closed.size();
    }

    private int getWinningTrades(List<PositionEntity> closed) {
        return (int) closed.stream()
            .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .count();
    }

    private BigDecimal calculateAvgWin(List<PositionEntity> closed) {
        List<BigDecimal> wins = closed.stream()
            .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .map(PositionEntity::getRealizedPnL)
            .toList();

        if (wins.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = wins.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(wins.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAvgLoss(List<PositionEntity> closed) {
        List<BigDecimal> losses = closed.stream()
            .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) < 0)
            .map(e -> e.getRealizedPnL().abs())
            .toList();

        if (losses.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(losses.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalValue() {
        return tradingService.getTotalValue();
    }

    private BigDecimal calculateProfitFactor(List<PositionEntity> closed) {
        if (closed.isEmpty()) return BigDecimal.ZERO;
        BigDecimal grossWins = closed.stream()
            .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .map(PositionEntity::getRealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLosses = closed.stream()
            .filter(e -> e.getRealizedPnL() != null && e.getRealizedPnL().compareTo(BigDecimal.ZERO) < 0)
            .map(e -> e.getRealizedPnL().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grossLosses.compareTo(BigDecimal.ZERO) == 0) {
            return grossWins.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
        }
        return grossWins.divide(grossLosses, 2, RoundingMode.HALF_UP);
    }

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
