package com.swingtrade.llm;

import java.time.LocalDateTime;

/**
 * Represents a technical trading signal generated from news analysis.
 */
public class TechnicalSignal {
    private String symbol;
    private String signalType;
    private String reason;
    private LocalDateTime timestamp;
    private Double strength;

    /**
     * Creates a new technical signal.
     * 
     * @param symbol The stock symbol
     * @param signalType The type of signal (BUY/SELL/HOLD)
     * @param reason The reasoning behind the signal
     * @param timestamp The time when the signal was generated
     * @param strength The strength of the signal (0.0-1.0)
     */
    public TechnicalSignal(String symbol, String signalType, String reason, LocalDateTime timestamp, Double strength) {
        this.symbol = symbol;
        this.signalType = signalType;
        this.reason = reason;
        this.timestamp = timestamp;
        this.strength = strength;
    }

    /**
     * Gets the stock symbol.
     * 
     * @return The stock symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the signal type.
     * 
     * @return The signal type (BUY/SELL/HOLD)
     */
    public String getSignalType() {
        return signalType;
    }

    /**
     * Gets the reasoning behind the signal.
     * 
     * @return The reasoning text
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the timestamp of when the signal was generated.
     * 
     * @return The timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the strength of the signal.
     * 
     * @return Signal strength (0.0-1.0)
     */
    public Double getStrength() {
        return strength;
    }

    @Override
    public String toString() {
        return "TechnicalSignal{" +
                "symbol='" + symbol + '\'' +
                ", signalType='" + signalType + '\'' +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                ", strength=" + strength +
                '}';
    }
}
