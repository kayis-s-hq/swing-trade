package com.swingtrade.api.dto;

/**
 * DTO for scan requests.
 */
public class ScanRequest {

    private String symbol;
    private Boolean includeSentiment;

    public ScanRequest() {
    }

    public ScanRequest(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Boolean getIncludeSentiment() {
        return includeSentiment;
    }

    public void setIncludeSentiment(Boolean includeSentiment) {
        this.includeSentiment = includeSentiment;
    }
}
