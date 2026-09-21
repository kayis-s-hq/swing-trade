package com.swingtrade.data.entity;

import com.swingtrade.domain.Signal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the Signal domain model.
 */
@Entity
@Table(name = "signals", indexes = {
    @Index(name = "idx_signals_symbol_date", columnList = "symbol, date", unique = false),
    @Index(name = "idx_signals_date", columnList = "date", unique = false)
})
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version = 0;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "signal_type", nullable = false, length = 10)
    private String signalType;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "entry_price", precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target", precision = 15, scale = 4)
    private BigDecimal target;

    @Column(name = "risk_reward", precision = 5, scale = 2)
    private BigDecimal riskReward;

    @Column(name = "indicators")
    private String indicators;

    @Column(name = "warning_flag", length = 50)
    private String warningFlag;

    @Column(name = "sentiment_score", length = 12)
    private String sentimentScore;

    @Column(name = "sentiment_reasoning", columnDefinition = "TEXT")
    private String sentimentReasoning;

    @Column(name = "generated_at")
    private LocalDate generatedAt;

    @Column(name = "strategy", nullable = false, length = 40)
    private String strategy = STRATEGY_DEFAULT;

    @Column(name = "strategy_version", nullable = false)
    private Integer strategyVersion = 1;

    /** SHA-256 of the strategy config params that produced this signal; null for older rows. */
    @Column(name = "params_hash", length = 64)
    private String paramsHash;

    // Warning flag constants
    public static final String WARNING_NONE = "";
    public static final String WARNING_NEUTRAL_SENTIMENT = "NEUTRAL_SENTIMENT";

    /**
     * Type-safe wrapper around the {@code warning_flag} column's known values. The column
     * itself stays a plain String (existing rows already persist {@link #NONE}'s value as
     * {@code WARNING_NONE} = {@code ""}, not the literal text {@code "NONE"}, so this enum's
     * {@link #code()} must match that, not {@code name()}, to avoid a silent on-disk value
     * change for new rows).
     */
    public enum WarningFlag {
        NONE(WARNING_NONE),
        PENDING_SENTIMENT("PENDING_SENTIMENT"),
        NEUTRAL_SENTIMENT(WARNING_NEUTRAL_SENTIMENT);

        private final String code;

        WarningFlag(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    // Strategy constants identifying which engine produced this signal
    public static final String STRATEGY_DEFAULT = "DEFAULT";
    public static final String STRATEGY_PRICE_ACTION = "PRICE_ACTION";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    public SignalEntity() {
    }

    public SignalEntity(Signal signal) {
        this(signal, WARNING_NONE);
    }

    public SignalEntity(Signal signal, String warningFlag) {
        this.symbol = signal.symbol();
        this.date = signal.date();
        this.signalType = signal.type().name();
        this.confidenceScore = signal.confidence();
        this.reasoning = signal.reasoning();
        this.entryPrice = signal.entryPrice();
        this.stopLoss = signal.stopLoss();
        this.target = signal.target();
        this.riskReward = signal.riskReward();
        this.indicators = signal.indicators();
        this.generatedAt = signal.generatedAt();
        this.warningFlag = warningFlag;
        this.sentimentScore = signal.sentimentScore();
        this.sentimentReasoning = signal.sentimentReasoning();
    }

    public static SignalEntity fromDomain(Signal signal) {
        return fromDomain(signal, WARNING_NONE);
    }

    public static SignalEntity fromDomain(Signal signal, String warningFlag) {
        return fromDomain(signal, warningFlag, STRATEGY_DEFAULT, 1);
    }

    public static SignalEntity fromDomain(Signal signal, String warningFlag,
                                          String strategy, Integer strategyVersion) {
        SignalEntity entity = new SignalEntity();
        entity.setSymbol(signal.symbol());
        entity.setDate(signal.date());
        entity.setSignalType(signal.type().name());
        entity.setConfidenceScore(signal.confidence());
        entity.setReasoning(signal.reasoning());
        entity.setEntryPrice(signal.entryPrice());
        entity.setStopLoss(signal.stopLoss());
        entity.setTarget(signal.target());
        entity.setRiskReward(signal.riskReward());
        entity.setIndicators(signal.indicators());
        entity.setGeneratedAt(signal.generatedAt());
        entity.setWarningFlag(warningFlag);
        entity.setSentimentScore(signal.sentimentScore());
        entity.setSentimentReasoning(signal.sentimentReasoning());
        entity.setStrategy(strategy == null || strategy.isBlank() ? STRATEGY_DEFAULT : strategy);
        entity.setStrategyVersion(strategyVersion == null || strategyVersion < 1 ? 1 : strategyVersion);
        return entity;
    }

    public Signal toDomain() {
        return new Signal(
            id,
            symbol,
            date,
            Signal.SignalType.valueOf(signalType),
            confidenceScore,
            reasoning,
            entryPrice,
            stopLoss,
            target,
            riskReward,
            indicators,
            generatedAt,
            sentimentScore,
            sentimentReasoning,
            strategy,
            strategyVersion
        );
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

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
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

    public BigDecimal getRiskReward() {
        return riskReward;
    }

    public void setRiskReward(BigDecimal riskReward) {
        this.riskReward = riskReward;
    }

    public String getIndicators() {
        return indicators;
    }

    public void setIndicators(String indicators) {
        this.indicators = indicators;
    }

    public String getWarningFlag() {
        return warningFlag;
    }

    public void setWarningFlag(String warningFlag) {
        this.warningFlag = warningFlag;
    }

    public String getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(String sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public String getSentimentReasoning() {
        return sentimentReasoning;
    }

    public void setSentimentReasoning(String sentimentReasoning) {
        this.sentimentReasoning = sentimentReasoning;
    }

    public LocalDate getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDate generatedAt) {
        this.generatedAt = generatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Integer getStrategyVersion() {
        return strategyVersion;
    }

    public String getParamsHash() {
        return paramsHash;
    }

    public void setParamsHash(String paramsHash) {
        this.paramsHash = paramsHash;
    }

    public void setStrategyVersion(Integer strategyVersion) {
        this.strategyVersion = strategyVersion;
    }

    public Boolean getProcessed() {
        return processed;
    }

    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }
}
