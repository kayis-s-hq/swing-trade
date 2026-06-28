package com.swingtrade.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a manual scan operation
 */
public class ScanResult {
    private LocalDateTime scanTime;
    private Integer signalsFound;
    private List<String> symbolsScanned;
    private String status;
    
    /**
     * Default constructor
     */
    public ScanResult() {}
    
    /**
     * Constructor with parameters
     * @param scanTime Time when scan was performed
     * @param signalsFound Number of signals found
     * @param symbolsScanned Symbols that were scanned
     * @param status Status of the scan operation
     */
    public ScanResult(LocalDateTime scanTime, Integer signalsFound, List<String> symbolsScanned, String status) {
        this.scanTime = scanTime;
        this.signalsFound = signalsFound;
        this.symbolsScanned = symbolsScanned;
        this.status = status;
    }
    
    // Getters and setters
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
    
    public Integer getSignalsFound() { return signalsFound; }
    public void setSignalsFound(Integer signalsFound) { this.signalsFound = signalsFound; }
    
    public List<String> getSymbolsScanned() { return symbolsScanned; }
    public void setSymbolsScanned(List<String> symbolsScanned) { this.symbolsScanned = symbolsScanned; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
