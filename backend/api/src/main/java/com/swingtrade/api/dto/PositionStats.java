package com.swingtrade.api.dto;

import java.math.BigDecimal;

public class PositionStats {
    private Integer totalPositions;
    private Integer openPositions;
    private Integer closedPositions;
    private Integer stoppedOut;
    private Integer targetHit;
    private BigDecimal totalPnL;
    private BigDecimal unrealizedPnL;
    private Double winRate;
    private Double averageHoldingPeriod;
    private String message;

    // Default constructor
    public PositionStats() {}

    // Constructor with all fields
    public PositionStats(Integer totalPositions, Integer openPositions, Integer closedPositions,
                         Integer stoppedOut, Integer targetHit, BigDecimal totalPnL,
                         BigDecimal unrealizedPnL, Double winRate, Double averageHoldingPeriod) {
        this.totalPositions = totalPositions;
        this.openPositions = openPositions;
        this.closedPositions = closedPositions;
        this.stoppedOut = stoppedOut;
        this.targetHit = targetHit;
        this.totalPnL = totalPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.winRate = winRate;
        this.averageHoldingPeriod = averageHoldingPeriod;
    }

    // Getters and Setters
    public Integer getTotalPositions() { return totalPositions; }
    public void setTotalPositions(Integer totalPositions) { this.totalPositions = totalPositions; }

    public Integer getOpenPositions() { return openPositions; }
    public void setOpenPositions(Integer openPositions) { this.openPositions = openPositions; }

    public Integer getClosedPositions() { return closedPositions; }
    public void setClosedPositions(Integer closedPositions) { this.closedPositions = closedPositions; }

    public Integer getStoppedOut() { return stoppedOut; }
    public void setStoppedOut(Integer stoppedOut) { this.stoppedOut = stoppedOut; }

    public Integer getTargetHit() { return targetHit; }
    public void setTargetHit(Integer targetHit) { this.targetHit = targetHit; }

    public BigDecimal getTotalPnL() { return totalPnL; }
    public void setTotalPnL(BigDecimal totalPnL) { this.totalPnL = totalPnL; }

    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public void setUnrealizedPnL(BigDecimal unrealizedPnL) { this.unrealizedPnL = unrealizedPnL; }

    public Double getWinRate() { return winRate; }
    public void setWinRate(Double winRate) { this.winRate = winRate; }

    public Double getAverageHoldingPeriod() { return averageHoldingPeriod; }
    public void setAverageHoldingPeriod(Double averageHoldingPeriod) { this.averageHoldingPeriod = averageHoldingPeriod; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
