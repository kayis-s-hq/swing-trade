package com.swingtrade.domain.service;

import com.swingtrade.domain.Signal;

import java.math.BigDecimal;

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
}
