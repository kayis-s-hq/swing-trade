package com.swingtrade.domain.service;

import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.ShadowPositionSnapshot;
import com.swingtrade.domain.Signal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Per-variant paper trading portfolio bookkeeping (plan §7.2/§7 Phase 5).
 *
 * <p>{@code TradingService} (backed by the single in-memory {@code PaperTradingEngine}) remains
 * the one live, order-executing engine for {@code portfolio_id = "default"} — used for the
 * CHAMPION variant, and for the pre-multi-strategy fallback where no variants are configured at
 * all. Making that shared {@code OrderManager}/{@code PositionManager} pair itself
 * multi-tenant across every SHADOW variant's positions would be a much larger rearchitecture; the
 * least-invasive correct alternative implemented here instead gives every SHADOW variant its own,
 * independent, capital-tracked paper execution path via {@link #executeVariantBuy} — simulating
 * fills directly against that variant's {@code paper_trading_portfolio} row (deducting cash,
 * persisting a portfolio-tagged {@code PaperTradingOrderEntity}) without touching the shared
 * engine's position book at all. Two variants' calls into this method never share mutable state,
 * so one variant's failure or capital exhaustion cannot affect another's execution.
 */
public interface PaperPortfolioService {

    /**
     * Creates a {@code paper_trading_portfolio} row for {@code portfolioId} seeded with
     * {@code paperCapital} if one does not already exist. Idempotent - a second call for the
     * same {@code portfolioId} is a no-op.
     *
     * @param portfolioId  the variant id (business key, {@code portfolio_id} column)
     * @param paperCapital starting capital; must not be null
     */
    void ensurePortfolio(String portfolioId, BigDecimal paperCapital);

    /**
     * Whether {@code portfolioId}'s realized+unrealized loss for the current trading day exceeds
     * its configured daily-loss threshold. Existing open positions may still be exited when this
     * is true; only new BUY entries should be blocked.
     *
     * @param portfolioId the portfolio to check
     * @return true if new BUY entries for this portfolio should be blocked for the rest of today
     */
    boolean isDailyLossBreached(String portfolioId);

    /**
     * Marks-to-market and snapshots every known portfolio (not just {@code "default"}) for the
     * daily EOD snapshot (finding F8).
     */
    void snapshotAllPortfolios();

    /**
     * Simulates a BUY fill for {@code signal} against {@code portfolioId}'s own paper portfolio,
     * independently of any other portfolio: sizes the position from that portfolio's own current
     * capital (same 1%-risk-per-trade sizing {@code PaperTradingEngine} uses), rejects it if
     * capacity/capital constraints are violated, and on success debits capital + commission from
     * the portfolio row and persists a portfolio-tagged order. Never mutates the shared "default"
     * engine's position book. Ensures the portfolio row exists first (creating it with the
     * variant's configured paper capital is the caller's responsibility via
     * {@link #ensurePortfolio}, called earlier in the pipeline).
     *
     * @param portfolioId    the variant id (portfolio_id column)
     * @param signal         the BUY signal to execute
     * @param referencePrice the reference/execution price
     * @return true if the BUY was simulated and persisted, false if rejected (e.g. insufficient
     *     capital/capacity) - the caller should leave the signal unprocessed for retry in that case
     */
    boolean executeVariantBuy(String portfolioId, Signal signal, BigDecimal referencePrice);

    /**
     * Returns {@code portfolioId}'s currently open shadow position for {@code symbol}, if any
     * (plan §7.4 gap-fill). At most one is expected to be open at a time per (portfolio, symbol).
     *
     * @param portfolioId the variant id (portfolio_id column)
     * @param symbol      the symbol to look up
     * @return the open position snapshot, or empty if none is open
     */
    Optional<ShadowPositionSnapshot> findOpenShadowPosition(String portfolioId, String symbol);

    /**
     * Advances the high-water-mark persisted for {@code portfolioId}'s open position on
     * {@code symbol} to {@code candidateClose} if it is higher, so a trailing stop evaluated on a
     * later run reflects the highest close observed since entry. A no-op if no position is open.
     *
     * @param portfolioId    the variant id (portfolio_id column)
     * @param symbol         the symbol
     * @param candidateClose today's close
     */
    void advanceShadowPositionHighWaterMark(String portfolioId, String symbol, BigDecimal candidateClose);

    /**
     * Simulates a SELL fill closing {@code portfolioId}'s open position on {@code symbol}
     * (plan §7.4 gap-fill): credits the portfolio's capital with proceeds minus commission,
     * decrements {@code open_position_count}, persists a portfolio-tagged SELL order, and marks
     * the position closed. Idempotent - if the position is already closed (e.g. a retried run
     * within the same day), this is a no-op returning false.
     *
     * @param portfolioId the variant id (portfolio_id column)
     * @param symbol      the symbol whose open position should be closed
     * @param exitPrice   the simulated exit fill price
     * @param exitReason  the {@code ExitReason} name (e.g. "STOP_LOSS", "TIME_STOP") that
     *                    triggered this exit - passed as a plain string so this domain interface
     *                    does not depend on the strategy module's exit-evaluation types
     * @return true if a position was found open and closed, false if there was nothing to close
     */
    boolean executeVariantExit(String portfolioId, String symbol, BigDecimal exitPrice, String exitReason);

    /**
     * Returns every closed round-trip trade for {@code portfolioId}, most recently exited first
     * (plan §7.4 gap-fill) - the data source for the champion/challenger promotion-eligibility
     * checker.
     *
     * @param portfolioId the variant id (portfolio_id column)
     */
    List<ShadowClosedTrade> findClosedTrades(String portfolioId);
}
