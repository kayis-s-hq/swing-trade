package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring application event fired when a paper trading position is closed.
 * Used by downstream modules to track sentiment accuracy and other metrics.
 */
public class PositionClosedEvent {

    private final String symbol;
    private final LocalDate signalDate;
    private final String outcome;
    private final BigDecimal pnlPct;

    public PositionClosedEvent(String symbol, LocalDate signalDate, String outcome, BigDecimal pnlPct) {
        this.symbol = symbol;
        this.signalDate = signalDate;
        this.outcome = outcome;
        this.pnlPct = pnlPct;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public String getOutcome() {
        return outcome;
    }

    public BigDecimal getPnlPct() {
        return pnlPct;
    }
}