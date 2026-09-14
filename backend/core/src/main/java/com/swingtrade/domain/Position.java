package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Position aggregate root. Entry, risk, valuation, and exit details are grouped
 * into immutable value records while compatibility accessors preserve the
 * existing domain contract during migration.
 */
public record Position(
    Long id,
    String brokerType,
    PositionEntry entry,
    PositionRisk risk,
    PositionValuation valuation,
    PositionStatus status,
    PositionExit exit,
    List<Order> orders
) {
    public Position {
        brokerType = brokerType != null ? brokerType : "PAPER";
        if (entry == null) {
            throw new IllegalArgumentException("Position entry is required");
        }
        risk = risk != null ? risk : new PositionRisk(null, null, BigDecimal.ZERO);
        valuation = valuation != null
            ? valuation
            : new PositionValuation(entry.entryPrice(), BigDecimal.ZERO, BigDecimal.ZERO);
        status = status != null ? status : PositionStatus.OPEN;
        if (entry.positionId() == null && id != null) {
            entry = PositionEntry.of(entry.symbol(), entry.entryPrice(), entry.entryDate(), entry.quantity(),
                entry.entryTime(), entry.entryReason(), "POS_" + String.format("%08d", id),
                entry.brokerPositionId(), entry.exchange(), entry.direction(), entry.averagePrice());
        }
    }

    /** Compatibility constructor for callers being migrated to grouped values. */
    public Position(
        Long id,
        String brokerType,
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        BigDecimal stopLoss,
        BigDecimal target,
        PositionStatus status,
        String entryReason,
        BigDecimal currentPrice,
        String positionId,
        String brokerPositionId,
        Exchange exchange,
        TradeDirection direction,
        BigDecimal averagePrice,
        BigDecimal unrealizedPnL,
        BigDecimal realizedPnL,
        BigDecimal marginUtilized,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        String exitReason,
        List<Order> orders
    ) {
        this(
            id,
            brokerType,
            PositionEntry.of(symbol, entryPrice, entryDate, quantity, entryTime, entryReason,
                positionId, brokerPositionId, exchange, direction, averagePrice),
            new PositionRisk(stopLoss, target, marginUtilized),
            new PositionValuation(currentPrice, unrealizedPnL, realizedPnL),
            status,
            exitTime == null && exitReason == null ? null : new PositionExit(exitTime, exitReason),
            orders
        );
    }

    /** Creates a position from persistence or another external adapter. */
    public static Position of(
        Long id,
        String brokerType,
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        BigDecimal stopLoss,
        BigDecimal target,
        PositionStatus status,
        String entryReason,
        BigDecimal currentPrice,
        String positionId,
        String brokerPositionId,
        Exchange exchange,
        TradeDirection direction,
        BigDecimal averagePrice,
        BigDecimal unrealizedPnL,
        BigDecimal realizedPnL,
        BigDecimal marginUtilized,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        String exitReason,
        List<Order> orders
    ) {
        return new Position(id, brokerType, symbol, entryPrice, entryDate, quantity, stopLoss, target,
            status, entryReason, currentPrice, positionId, brokerPositionId, exchange, direction,
            averagePrice, unrealizedPnL, realizedPnL, marginUtilized, entryTime, exitTime, exitReason,
            orders);
    }

    /** Creates a paper position with a precomputed risk envelope. */
    public static Position openPaper(
        String positionId,
        String symbol,
        TradeDirection direction,
        Integer quantity,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        String entryReason,
        LocalDate entryDate,
        LocalDateTime entryTime
    ) {
        return new Position(null, "PAPER",
            PositionEntry.of(symbol, entryPrice, entryDate, quantity, entryTime, entryReason,
                positionId, null, Exchange.NSE, direction, entryPrice),
            new PositionRisk(stopLoss, target, BigDecimal.ZERO),
            new PositionValuation(entryPrice, BigDecimal.ZERO, BigDecimal.ZERO),
            PositionStatus.OPEN, null, null);
    }

    public static Position createWithRisk(
        String symbol,
        BigDecimal entryPrice,
        LocalDate entryDate,
        Integer quantity,
        BigDecimal atr,
        String entryReason
    ) {
        BigDecimal stopLoss = entryPrice.subtract(atr.multiply(BigDecimal.valueOf(2)));
        BigDecimal risk = entryPrice.subtract(stopLoss);
        BigDecimal target = entryPrice.add(risk.multiply(BigDecimal.valueOf(2.5)));
        return new Position(null, "PAPER",
            PositionEntry.of(symbol, entryPrice, entryDate, quantity, null, entryReason,
                null, null, Exchange.NSE, TradeDirection.LONG, entryPrice),
            new PositionRisk(stopLoss, target, BigDecimal.ZERO),
            new PositionValuation(entryPrice, BigDecimal.ZERO, BigDecimal.ZERO),
            PositionStatus.OPEN, null, null);
    }

    public Position withValuation(BigDecimal currentPrice, BigDecimal unrealizedPnL) {
        return new Position(id, brokerType, entry, risk,
            new PositionValuation(currentPrice, unrealizedPnL, realizedPnL()), status, exit, orders);
    }

    public Position withQuantityAndRealizedPnL(Integer quantity, BigDecimal realizedPnL) {
        PositionEntry updatedEntry = PositionEntry.of(symbol(), entryPrice(), entryDate(), quantity,
            entryTime(), entryReason(), positionId(), brokerPositionId(), exchange(), direction(), averagePrice());
        return new Position(id, brokerType, updatedEntry, risk,
            new PositionValuation(currentPrice(), unrealizedPnL(), realizedPnL), status, exit, orders);
    }

    public Position close(PositionStatus newStatus, BigDecimal realizedPnL, LocalDateTime exitTime,
                          String exitReason, BigDecimal target) {
        return new Position(id, brokerType, entry,
            new PositionRisk(stopLoss(), target, marginUtilized()),
            new PositionValuation(currentPrice(), unrealizedPnL(), realizedPnL), newStatus,
            new PositionExit(exitTime, exitReason), orders);
    }

    // Compatibility accessors while callers migrate to entry/risk/valuation/exit.
    public String symbol() { return entry.symbol(); }
    public BigDecimal entryPrice() { return entry.entryPrice(); }
    public LocalDate entryDate() { return entry.entryDate(); }
    public Integer quantity() { return entry.quantity(); }
    public BigDecimal stopLoss() { return risk.stopLoss(); }
    public BigDecimal target() { return risk.target(); }
    public String entryReason() { return entry.entryReason(); }
    public BigDecimal currentPrice() { return valuation.currentPrice(); }
    public String positionId() { return entry.positionId(); }
    public String brokerPositionId() { return entry.brokerPositionId(); }
    public Exchange exchange() { return entry.exchange(); }
    public TradeDirection direction() { return entry.direction(); }
    public BigDecimal averagePrice() { return entry.averagePrice(); }
    public BigDecimal unrealizedPnL() { return valuation.unrealizedPnL(); }
    public BigDecimal realizedPnL() { return valuation.realizedPnL(); }
    public BigDecimal marginUtilized() { return risk.marginUtilized(); }
    public LocalDateTime entryTime() { return entry.entryTime(); }
    public LocalDateTime exitTime() { return exit != null ? exit.exitTime() : null; }
    public String exitReason() { return exit != null ? exit.exitReason() : null; }

    public BigDecimal calculateUnrealizedPnL(BigDecimal currentPrice) {
        BigDecimal priceDifference = direction() == TradeDirection.SHORT
            ? entryPrice().subtract(currentPrice)
            : currentPrice.subtract(entryPrice());
        return priceDifference.multiply(BigDecimal.valueOf(quantity()));
    }

    public BigDecimal calculatePnLPercent(BigDecimal currentPrice) {
        BigDecimal pnl = calculateUnrealizedPnL(currentPrice);
        BigDecimal costBasis = entryPrice().multiply(BigDecimal.valueOf(quantity()));
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return pnl.multiply(BigDecimal.valueOf(100)).divide(costBasis, 4, BigDecimal.ROUND_HALF_UP);
    }

    public boolean isOpen() { return PositionStatus.OPEN == status; }

    public boolean isClosed() {
        return PositionStatus.CLOSED == status
            || PositionStatus.STOPPED == status
            || PositionStatus.TARGET_HIT == status;
    }
}
