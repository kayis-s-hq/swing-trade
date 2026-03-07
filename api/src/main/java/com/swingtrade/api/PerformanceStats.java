package com.swingtrade.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Performance statistics for backtesting and paper trading
 */
public class PerformanceStats {
    private BigDecimal totalReturn;
    private BigDecimal annualizedReturn;
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private Integer totalTrades;
    private Integer winningTrades;
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private LocalDateTime asOfDate;
    
    /**
     * Default constructor
     */
    public PerformanceStats() {}
    
    /**
     * Constructor with parameters
     * @param totalReturn Total return percentage
     * @param annualizedReturn Annualized return
     * @param sharpeRatio Sharpe ratio
     * @param maxDrawdown Maximum drawdown
     * @param totalTrades Total number of trades
     * @param winningTrades Number of winning trades
     * @param averageWin Average win amount
     * @param averageLoss Average loss amount
     * @param asOfDate Statistics as of this date
     */
    public PerformanceStats(BigDecimal totalReturn, BigDecimal annualizedReturn, BigDecimal sharpeRatio,
                           BigDecimal maxDrawdown, Integer totalTrades, Integer winningTrades,
                           BigDecimal averageWin, BigDecimal averageLoss, LocalDateTime asOfDate) {
        this.totalReturn = totalReturn;
        this.annualizedReturn = annualizedReturn;
        this.sharpeRatio = sharpeRatio;
        this.maxDrawdown = maxDrawdown;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.averageWin = averageWin;
        this.averageLoss = averageLoss;
        this.asOfDate = asOfDate;
    }
    
    // Getters and setters
    public BigDecimal getTotalReturn() { return totalReturn; }
    public void setTotalReturn(BigDecimal totalReturn) { this.totalReturn = totalReturn; }
    
    public BigDecimal getAnnualizedReturn() { return annualizedReturn; }
    public void setAnnualizedReturn(BigDecimal annualizedReturn) { this.annualizedReturn = annualizedReturn; }
    
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    
    public Integer getTotalTrades() { return totalTrades; }
    public void setTotalTrades(Integer totalTrades) { this.totalTrades = totalTrades; }
    
    public Integer getWinningTrades() { return winningTrades; }
    public void setWinningTrades(Integer winningTrades) { this.winningTrades = winningTrades; }
    
    public BigDecimal getAverageWin() { return averageWin; }
    public void setAverageWin(BigDecimal averageWin) { this.averageWin = averageWin; }
    
    public BigDecimal getAverageLoss() { return averageLoss; }
    public void setAverageLoss(BigDecimal averageLoss) { this.averageLoss = averageLoss; }
    
    public LocalDateTime getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDateTime asOfDate) { this.asOfDate = asOfDate; }
}
