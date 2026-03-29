package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for scanning operations.
 * Returns the results of market scanning for trading opportunities.
 */
public class ScanResponse {

    private LocalDateTime scanTime;
    private ScanStatus status;
    private Integer symbolsScanned;
    private Integer signalsFound;
    private Integer buySignals;
    private Integer sellSignals;
    private Integer holdSignals;
    private List<String> scannedSymbols;
    private List<ScanSignalResult> scanResults;
    private String message;

    public ScanResponse() {
    }

    // Getters and Setters
    public LocalDateTime getScanTime() {
        return scanTime;
    }

    public void setScanTime(LocalDateTime scanTime) {
        this.scanTime = scanTime;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public Integer getSymbolsScanned() {
        return symbolsScanned;
    }

    public void setSymbolsScanned(Integer symbolsScanned) {
        this.symbolsScanned = symbolsScanned;
    }

    public Integer getSignalsFound() {
        return signalsFound;
    }

    public void setSignalsFound(Integer signalsFound) {
        this.signalsFound = signalsFound;
    }

    public Integer getBuySignals() {
        return buySignals;
    }

    public void setBuySignals(Integer buySignals) {
        this.buySignals = buySignals;
    }

    public Integer getSellSignals() {
        return sellSignals;
    }

    public void setSellSignals(Integer sellSignals) {
        this.sellSignals = sellSignals;
    }

    public Integer getHoldSignals() {
        return holdSignals;
    }

    public void setHoldSignals(Integer holdSignals) {
        this.holdSignals = holdSignals;
    }

    public List<String> getScannedSymbols() {
        return scannedSymbols;
    }

    public void setScannedSymbols(List<String> scannedSymbols) {
        this.scannedSymbols = scannedSymbols;
    }

    public List<ScanSignalResult> getScanResults() {
        return scanResults;
    }

    public void setScanResults(List<ScanSignalResult> scanResults) {
        this.scanResults = scanResults;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Enum for scan status.
     */
    public enum ScanStatus {
        COMPLETED("Completed"),
        IN_PROGRESS("In Progress"),
        FAILED("Failed"),
        NO_SIGNALS("No Signals Found");

        private final String description;

        ScanStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Individual scan result for a symbol.
     */
    public static class ScanSignalResult {
        private String symbol;
        private SignalResponse.SignalType signalType;
        private java.math.BigDecimal confidence;
        private String reasoning;
        private java.math.BigDecimal entryPrice;
        private java.math.BigDecimal stopLoss;
        private java.math.BigDecimal target;
        private java.util.List<String> indicators;

        public ScanSignalResult() {
        }

        public ScanSignalResult(String symbol, SignalResponse.SignalType signalType,
                                java.math.BigDecimal confidence, String reasoning) {
            this.symbol = symbol;
            this.signalType = signalType;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }

        // Getters and Setters
        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public SignalResponse.SignalType getSignalType() {
            return signalType;
        }

        public void setSignalType(SignalResponse.SignalType signalType) {
            this.signalType = signalType;
        }

        public java.math.BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(java.math.BigDecimal confidence) {
            this.confidence = confidence;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public java.math.BigDecimal getEntryPrice() {
            return entryPrice;
        }

        public void setEntryPrice(java.math.BigDecimal entryPrice) {
            this.entryPrice = entryPrice;
        }

        public java.math.BigDecimal getStopLoss() {
            return stopLoss;
        }

        public void setStopLoss(java.math.BigDecimal stopLoss) {
            this.stopLoss = stopLoss;
        }

        public java.math.BigDecimal getTarget() {
            return target;
        }

        public void setTarget(java.math.BigDecimal target) {
            this.target = target;
        }

        public java.util.List<String> getIndicators() {
            return indicators;
        }

        public void setIndicators(java.util.List<String> indicators) {
            this.indicators = indicators;
        }
    }
}
