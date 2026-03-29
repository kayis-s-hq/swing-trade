package com.swingtrade.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public class SectorAllocation {
    private Map<String, Double> allocation;
    private BigDecimal totalExposure;
    private Integer numberOfSectors;
    private String message;

    // Default constructor
    public SectorAllocation() {}

    // Constructor with all fields
    public SectorAllocation(Map<String, Double> allocation, BigDecimal totalExposure, Integer numberOfSectors) {
        this.allocation = allocation;
        this.totalExposure = totalExposure;
        this.numberOfSectors = numberOfSectors;
    }

    // Getters and Setters
    public Map<String, Double> getAllocation() { return allocation; }
    public void setAllocation(Map<String, Double> allocation) { this.allocation = allocation; }

    public BigDecimal getTotalExposure() { return totalExposure; }
    public void setTotalExposure(BigDecimal totalExposure) { this.totalExposure = totalExposure; }

    public Integer getNumberOfSectors() { return numberOfSectors; }
    public void setNumberOfSectors(Integer numberOfSectors) { this.numberOfSectors = numberOfSectors; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
