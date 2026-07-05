package com.swingtrade.api.dto;

import com.swingtrade.domain.Signal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for trading signals.
 * Represents the complete signal information including technical analysis factors.
 */
public class SignalResponse {

    private Long id;
    private String symbol;
    private LocalDate date;
    private SignalType signalType;
    private BigDecimal confidence;
    private String reasoning;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal target;
    private BigDecimal riskRewardRatio;
    private List<String> indicators;
    private LocalDate generatedAt;
    private String strategy;

    public SignalResponse() {
    }

    public SignalResponse(String symbol, LocalDate date, SignalType signalType,
                          BigDecimal confidence, String reasoning, BigDecimal entryPrice,
                          BigDecimal stopLoss, BigDecimal target, BigDecimal riskRewardRatio,
                          List<String> indicators, java.time.LocalDateTime generatedAt) {
        this.symbol = symbol;
        this.date = date;
        this.signalType = signalType;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.target = target;
        this.riskRewardRatio = riskRewardRatio;
        this.indicators = indicators;
        this.generatedAt = generatedAt.toLocalDate();
    }

    public SignalResponse(Signal signal) {
        this.id = signal.id();
        this.symbol = signal.symbol();
        this.date = signal.date();
        this.signalType = SignalType.valueOf(signal.type().name());
        this.confidence = signal.confidence();
        this.reasoning = signal.reasoning();
        this.entryPrice = signal.entryPrice();
        this.stopLoss = signal.stopLoss();
        this.target = signal.target();
        this.riskRewardRatio = signal.riskReward();
        this.indicators = parseIndicators(signal.indicators());
        this.generatedAt = signal.generatedAt();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public BigDecimal getRiskRewardRatio() {
        return riskRewardRatio;
    }

    public void setRiskRewardRatio(BigDecimal riskRewardRatio) {
        this.riskRewardRatio = riskRewardRatio;
    }

    public List<String> getIndicators() {
        return indicators;
    }

    public void setIndicators(List<String> indicators) {
        this.indicators = indicators;
    }

    public LocalDate getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDate generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * Parses indicators string into a list (comma-separated values).
     */
    private List<String> parseIndicators(String indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return List.of();
        }
        return List.of(indicators.split(","));
    }

    /**
     * Enum mapping for SignalType.
     */
    public enum SignalType {
        BUY("Buy Signal"),
        SELL("Sell Signal"),
        HOLD("Hold");

        private final String description;

        SignalType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
