package com.swingtrade.domain.service;

import java.math.BigDecimal;

/**
 * Per-variant paper trading portfolio bookkeeping (plan §7.2).
 *
 * <p>This is deliberately separate from {@link TradingService}: {@code TradingService} (backed
 * by the single in-memory {@code PaperTradingEngine}) still only executes orders against one
 * live portfolio ({@code portfolio_id = "default"}, currently used for the CHAMPION variant).
 * Making order execution itself portfolio-parametrized (so every SHADOW variant gets its own
 * independently-simulated fills) is a larger engine rearchitecture deferred out of this phase -
 * see {@code JobOrchestratorService.stagePaperTrade}'s comment for the interpretation applied
 * here. This interface covers what §7.2 needs regardless of that: a persisted
 * {@code paper_trading_portfolio} row per variant, daily snapshots per portfolio, and a
 * per-portfolio daily loss breaker.
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
}
