package com.swingtrade.domain;

/**
 * Recorded intent for what happens to a variant's virtual paper portfolio when a new
 * {@link StrategyConfig} version is created for it (plan §4.5).
 *
 * <p>This phase only records the caller's chosen action alongside the new version; actually
 * manipulating open virtual positions (closing them at next open, restoring paper capital) is
 * deferred to the phase that builds per-variant paper portfolios (plan §7 / Phase 5), since that
 * depends on per-variant portfolio infrastructure that does not exist yet.
 */
public enum PortfolioAction {
    /** Open positions keep the old version's exits; new entries use the new version. */
    CONTINUE,
    /** Close virtual positions at next open (CONFIG_RESET), restore paper_capital. */
    RESET
}
