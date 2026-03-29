package com.swingtrade.api.dto;

import java.time.LocalDate;

/**
 * DTO for combined technical and sentiment signal responses.
 */
public class CombinedSignalResponse {

    private String symbol;
    private LocalDate analysisDate;
    private SignalResponse.SignalType signalType;
    private TechnicalAnalysisResponse technicalAnalysis;
    private SentimentAnalysisResponse sentimentAnalysis;

    public CombinedSignalResponse() {
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

    public SignalResponse.SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalResponse.SignalType signalType) {
        this.signalType = signalType;
    }

    public TechnicalAnalysisResponse getTechnicalAnalysis() {
        return technicalAnalysis;
    }

    public void setTechnicalAnalysis(TechnicalAnalysisResponse technicalAnalysis) {
        this.technicalAnalysis = technicalAnalysis;
    }

    public SentimentAnalysisResponse getSentimentAnalysis() {
        return sentimentAnalysis;
    }

    public void setSentimentAnalysis(SentimentAnalysisResponse sentimentAnalysis) {
        this.sentimentAnalysis = sentimentAnalysis;
    }
}
