package com.swingtrade.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured output POJO for LLM synthesis analysis.
 * Used with Spring AI BeanOutputConverter for typed structured output.
 */
public class SynthesisOutput {
    private String narrative;
    private String recommendation;
    private Double confidence;
    private List<String> keyDrivers = new ArrayList<>();
    private List<String> bullishFactors = new ArrayList<>();
    private List<String> bearishFactors = new ArrayList<>();
    private boolean conflictDetected;
    private boolean eventRiskDetected;
    private String eventRiskReason;

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getKeyDrivers() {
        return keyDrivers;
    }

    public void setKeyDrivers(List<String> keyDrivers) {
        this.keyDrivers = keyDrivers;
    }

    public List<String> getBullishFactors() {
        return bullishFactors;
    }

    public void setBullishFactors(List<String> bullishFactors) {
        this.bullishFactors = bullishFactors;
    }

    public List<String> getBearishFactors() {
        return bearishFactors;
    }

    public void setBearishFactors(List<String> bearishFactors) {
        this.bearishFactors = bearishFactors;
    }

    public boolean isConflictDetected() { return conflictDetected; }

    public void setConflictDetected(boolean conflictDetected) { this.conflictDetected = conflictDetected; }

    public boolean isEventRiskDetected() { return eventRiskDetected; }

    public void setEventRiskDetected(boolean eventRiskDetected) { this.eventRiskDetected = eventRiskDetected; }

    public String getEventRiskReason() { return eventRiskReason; }

    public void setEventRiskReason(String eventRiskReason) { this.eventRiskReason = eventRiskReason; }

    @Override
    public String toString() {
        return "SynthesisOutput{" +
                "narrative='" + narrative + '\'' +
                ", recommendation='" + recommendation + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
