-- V50: Per-variant paper portfolio support (plan §7.2).
--
-- paper_trading_portfolio's `id` column stays a manually-assigned surrogate key (see
-- PaperTradingPortfolioEntity's comment on why it deliberately has no @GeneratedValue) - this
-- migration does not touch that. `portfolio_id` (VARCHAR, widened to 40 chars by V47) remains
-- the business key: PaperPortfolioServiceImpl.ensurePortfolio() looks rows up by it and assigns
-- a fresh surrogate id (max(id)+1) for new rows, so multiple portfolio_id rows can now coexist
-- (previously every caller hardcoded id=1L, so only one row - "default" - could ever exist).
--
-- daily_loss_threshold_pct backs the per-portfolio kill switch / daily loss breaker (plan
-- §7.2's third bullet): default 0.04 (4% of paper_capital) is a sane starting point per the
-- phase task's suggested 3-5% range.
ALTER TABLE paper_trading_portfolio
    ADD COLUMN daily_loss_threshold_pct NUMERIC(5,4) NOT NULL DEFAULT 0.04;

-- paper_trading_portfolio_snapshots.portfolio_id was added by V47 but nothing populated it
-- (finding F8) and PaperTradingSnapshotEntity had no mapped field for it. No schema change
-- needed here beyond what V47 already did; this comment documents that the Java-side gap
-- (PaperTradingSnapshotEntity now maps portfolio_id) is closed in this same commit.
