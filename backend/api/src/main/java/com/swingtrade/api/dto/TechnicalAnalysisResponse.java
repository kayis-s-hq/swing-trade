package com.swingtrade.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for technical analysis responses.
 */
public class TechnicalAnalysisResponse {

    private String symbol;
    private LocalDate analysisDate;
    private List<String> indicators;
    private String signal;

    public TechnicalAnalysisResponse() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(LocalDate analysisDate) {
        this.analysisDate = analysisDate;
    }

    public List<String> getIndicators() {
        return indicators;
    }

    public void setIndicators(List<String> indicators) {
        this.indicators = indicators;
    }

    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }
}
