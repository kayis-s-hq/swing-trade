package com.swingtrade.api.dto;

import com.swingtrade.domain.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Response DTO for completed trades.
 * Represents a complete trade lifecycle from entry to exit.
 */
public class TradeResponse {

    private Long id;
    private Long positionId;
    private String symbol;
    private LocalDate entryDate;
    private LocalDate exitDate;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private Integer quantity;
    private BigDecimal totalPnL;
    private BigDecimal pnlPercent;
    private Integer durationDays;
    private TradeStatus tradeStatus;
    private String entryReason;
    private String exitReason;
    private BigDecimal fees;

    public TradeResponse() {
    }

    public TradeResponse(Trade trade) {
        this.id = trade.id();
        this.positionId = trade.positionId();
        this.symbol = trade.symbol();
        this.entryDate = trade.entryDate();
        this.exitDate = trade.exitDate();
        this.entryPrice = trade.entryPrice();
        this.exitPrice = trade.exitPrice();
        this.quantity = trade.quantity();
        this.totalPnL = trade.totalPnL();

        // Calculate P&L percentage
        if (entryPrice != null && exitPrice != null && entryPrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
            this.pnlPercent = exitPrice.subtract(entryPrice)
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(entryPrice, 4, RoundingMode.HALF_UP);
        }

        this.durationDays = trade.durationDays();
        this.tradeStatus = TradeStatus.valueOf(trade.tradeStatus().name());
        this.entryReason = trade.entryReason();
        this.exitReason = trade.exitReason();
        this.fees = trade.fees();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalPnL() {
        return totalPnL;
    }

    public void setTotalPnL(BigDecimal totalPnL) {
        this.totalPnL = totalPnL;
    }

    public BigDecimal getPnlPercent() {
        return pnlPercent;
    }

    public void setPnlPercent(BigDecimal pnlPercent) {
        this.pnlPercent = pnlPercent;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public TradeStatus getTradeStatus() {
        return tradeStatus;
    }

    public void setTradeStatus(TradeStatus tradeStatus) {
        this.tradeStatus = tradeStatus;
    }

    public String getEntryReason() {
        return entryReason;
    }

    public void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    /**
     * Enum mapping for TradeStatus.
     */
    public enum TradeStatus {
        OPEN("Open"),
        CLOSED("Closed - Profit"),
        STOPPED("Stopped - Loss"),
        TARGET_HIT("Target Hit - Profit"),
        TIME_STOP("Time Stop");

        private final String displayName;

        TradeStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
