package com.swingtrade.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for trading performance statistics.
 * Aggregates key metrics for evaluating trading system effectiveness.
 */
public class PerformanceResponse {

    private BigDecimal totalReturn;
    private BigDecimal annualizedReturn;
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private BigDecimal sortinoRatio;
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private BigDecimal winRate;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private BigDecimal averageTrade;
    private BigDecimal profitFactor;
    private Integer longestWinStreak;
    private Integer longestLossStreak;
    private Integer averageHoldingPeriod;
    private LocalDateTime asOfDate;
    private BigDecimal totalCapitalGained;
    private BigDecimal totalFeesPaid;
    private Integer closedTrades;
    private BigDecimal totalPnL;
    private BigDecimal totalValue;

    public PerformanceResponse() {
    }

    // Getters and Setters
    public BigDecimal getTotalReturn() {
        return totalReturn;
    }

    public void setTotalReturn(BigDecimal totalReturn) {
        this.totalReturn = totalReturn;
    }

    public BigDecimal getAnnualizedReturn() {
        return annualizedReturn;
    }

    public void setAnnualizedReturn(BigDecimal annualizedReturn) {
        this.annualizedReturn = annualizedReturn;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public BigDecimal getSortinoRatio() {
        return sortinoRatio;
    }

    public void setSortinoRatio(BigDecimal sortinoRatio) {
        this.sortinoRatio = sortinoRatio;
    }

    public Integer getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(Integer totalTrades) {
        this.totalTrades = totalTrades;
    }

    public Integer getWinningTrades() {
        return winningTrades;
    }

    public void setWinningTrades(Integer winningTrades) {
        this.winningTrades = winningTrades;
    }

    public Integer getLosingTrades() {
        return losingTrades;
    }

    public void setLosingTrades(Integer losingTrades) {
        this.losingTrades = losingTrades;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public void setWinRate(BigDecimal winRate) {
        this.winRate = winRate;
    }

    public BigDecimal getAverageWin() {
        return averageWin;
    }

    public void setAverageWin(BigDecimal averageWin) {
        this.averageWin = averageWin;
    }

    public BigDecimal getAverageLoss() {
        return averageLoss;
    }

    public void setAverageLoss(BigDecimal averageLoss) {
        this.averageLoss = averageLoss;
    }

    public BigDecimal getAverageTrade() {
        return averageTrade;
    }

    public void setAverageTrade(BigDecimal averageTrade) {
        this.averageTrade = averageTrade;
    }

    public BigDecimal getProfitFactor() {
        return profitFactor;
    }

    public void setProfitFactor(BigDecimal profitFactor) {
        this.profitFactor = profitFactor;
    }

    public Integer getLongestWinStreak() {
        return longestWinStreak;
    }

    public void setLongestWinStreak(Integer longestWinStreak) {
        this.longestWinStreak = longestWinStreak;
    }

    public Integer getLongestLossStreak() {
        return longestLossStreak;
    }

    public void setLongestLossStreak(Integer longestLossStreak) {
        this.longestLossStreak = longestLossStreak;
    }

    public Integer getAverageHoldingPeriod() {
        return averageHoldingPeriod;
    }

    public void setAverageHoldingPeriod(Integer averageHoldingPeriod) {
        this.averageHoldingPeriod = averageHoldingPeriod;
    }

    public LocalDateTime getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDateTime asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getTotalCapitalGained() {
        return totalCapitalGained;
    }

    public void setTotalCapitalGained(BigDecimal totalCapitalGained) {
        this.totalCapitalGained = totalCapitalGained;
    }

    public BigDecimal getTotalFeesPaid() {
        return totalFeesPaid;
    }

    public void setTotalFeesPaid(BigDecimal totalFeesPaid) {
        this.totalFeesPaid = totalFeesPaid;
    }

    public Integer getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(Integer closedTrades) {
        this.closedTrades = closedTrades;
    }

    public BigDecimal getTotalPnL() {
        return totalPnL;
    }

    public void setTotalPnL(BigDecimal totalPnL) {
        this.totalPnL = totalPnL;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    /**
     * Creates a PerformanceResponse from individual values.
     */
    public static PerformanceResponse of(
        BigDecimal totalReturn,
        BigDecimal annualizedReturn,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdown,
        Integer totalTrades,
        Integer winningTrades,
        BigDecimal totalPnL,
        BigDecimal totalValue,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        BigDecimal profitFactor
    ) {
        PerformanceResponse response = new PerformanceResponse();
        response.setTotalReturn(totalReturn);
        response.setAnnualizedReturn(annualizedReturn);
        response.setSharpeRatio(sharpeRatio);
        response.setMaxDrawdown(maxDrawdown);
        response.setTotalTrades(totalTrades);
        response.setWinningTrades(winningTrades);
        response.setTotalPnL(totalPnL);
        response.setTotalValue(totalValue);
        response.setAverageWin(averageWin);
        response.setAverageLoss(averageLoss);
        response.setProfitFactor(profitFactor);

        // Calculate derived metrics
        if (totalTrades != null && totalTrades > 0 && winningTrades != null) {
            response.setLosingTrades(totalTrades - winningTrades);
            response.setWinRate(BigDecimal.valueOf(winningTrades)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTrades), 2, java.math.RoundingMode.HALF_UP));
        }

        response.setAsOfDate(LocalDateTime.now());
        return response;
    }
}
