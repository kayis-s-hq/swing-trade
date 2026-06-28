package com.swingtrade.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public class RiskSummary {
    private BigDecimal totalExposure;
    private BigDecimal availableCapital;
    private BigDecimal usedCapital;
    private BigDecimal stopLossExposure;
    private Integer numberOfPositions;
    private Map<String, Integer> sectorExposure;

    // Default constructor
    public RiskSummary() {}

    // Getters and Setters
    public BigDecimal getTotalExposure() { return totalExposure; }
    public void setTotalExposure(BigDecimal totalExposure) { this.totalExposure = totalExposure; }

    public BigDecimal getAvailableCapital() { return availableCapital; }
    public void setAvailableCapital(BigDecimal availableCapital) { this.availableCapital = availableCapital; }

    public BigDecimal getUsedCapital() { return usedCapital; }
    public void setUsedCapital(BigDecimal usedCapital) { this.usedCapital = usedCapital; }

    public BigDecimal getStopLossExposure() { return stopLossExposure; }
    public void setStopLossExposure(BigDecimal stopLossExposure) { this.stopLossExposure = stopLossExposure; }

    public Integer getNumberOfPositions() { return numberOfPositions; }
    public void setNumberOfPositions(Integer numberOfPositions) { this.numberOfPositions = numberOfPositions; }

    public Map<String, Integer> getSectorExposure() { return sectorExposure; }
    public void setSectorExposure(Map<String, Integer> sectorExposure) { this.sectorExposure = sectorExposure; }
}
