package com.swingtrade.domain.service;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-strategy-variant paper trading: every active (SHADOW or CHAMPION) strategy
 * variant simulates its own BUY/exit fills against its own portfolio bookkeeping,
 * keyed by {@code StrategyConfig.variantId()}, independent of the single legacy
 * "default" {@code PaperTradingEngine} portfolio (which continues to back the
 * champion's real order-queueing path via {@link TradingService}).
 *
 * <p>Implemented by {@code VariantPaperTradingService} in the broker module.
 * Deliberately a separate interface (not folded into {@link TradingService}) so
 * the api module can depend on it without any broker-internal type leaking across
 * the module boundary — see {@code ModuleBoundaryTest}.
 */
public interface VariantTradingService {

    /**
     * Idempotently ensures a paper-trading portfolio row exists for the given
     * variant, seeded with {@code paperCapital} if this is the first time the
     * variant has been made SHADOW/CHAMPION. A no-op if the portfolio already
     * exists (capital is not reset on repeated calls).
     */
    void ensurePortfolio(String variantId, BigDecimal paperCapital);

    /**
     * Opens a simulated position for {@code variantId}'s own portfolio from a BUY
     * signal, sized by that portfolio's available capital. No-ops (returns false)
     * if capital is insufficient, a position is already open for the symbol in
     * this portfolio, or the signal lacks the fields needed to size/risk it.
     *
     * @return true if a position was opened
     */
    boolean openPosition(String variantId, Signal buySignal, BigDecimal referencePrice);

    /**
     * Closes {@code variantId}'s open position (if any) for {@code symbol} at
     * {@code exitPrice}, crediting the variant's portfolio capital with the
     * proceeds and booking realized P&amp;L.
     *
     * @return true if a position was closed
     */
    boolean closePosition(String variantId, String symbol, BigDecimal exitPrice, String reason);

    /**
     * Checks {@code variantId}'s open position(s) for {@code symbol} against the
     * day's candle for a stop-loss/target hit, using the same trigger logic the
     * champion's own positions are evaluated with, and closes any that trigger.
     *
     * @return the number of positions closed by this check
     */
    int evaluateOpenPositions(String variantId, String symbol, OhlcvCandle candle);

    /**
     * {@code variantId}'s currently open positions for {@code symbol}, so strategy-driven exit
     * rules ({@code SignalStrategy.evaluateExit}) can be evaluated against them.
     */
    List<Position> findOpenPositions(String variantId, String symbol);

    /** All closed trades booked against {@code variantId}'s own portfolio. */
    List<Position> findClosedTrades(String variantId);
}
