-- V27: Backfill NULL version columns left by V25
--
-- V25 added `version integer` columns (no DEFAULT, no backfill) to 21 tables for
-- Hibernate @Version optimistic locking. Any row that existed before V25 ran was left
-- with version = NULL. Hibernate's VersionGeneration#generate -> IntegerJavaType#next
-- unconditionally calls current.intValue() when incrementing on UPDATE, so any UPDATE
-- of a pre-V25 row throws a NullPointerException wrapped in
-- jakarta.persistence.RollbackException, aborting the transaction.
--
-- Confirmed live-blocking: PaperTradingStateService.savePortfolio() updates the
-- singleton paper_trading_portfolio row (id=1, version=NULL), so every paper trade
-- fails to persist portfolio capital/P&L after this NPE, even though the position
-- row itself (freshly inserted with version=0) commits successfully — leaving the
-- position and portfolio state inconsistent.

UPDATE app_settings SET version = 0 WHERE version IS NULL;
UPDATE daily_loss_circuit_breaker_state SET version = 0 WHERE version IS NULL;
UPDATE fyers_symbol_master SET version = 0 WHERE version IS NULL;
UPDATE job_run_stages SET version = 0 WHERE version IS NULL;
UPDATE job_runs SET version = 0 WHERE version IS NULL;
UPDATE news_articles SET version = 0 WHERE version IS NULL;
UPDATE news_items SET version = 0 WHERE version IS NULL;
UPDATE nse_holidays SET version = 0 WHERE version IS NULL;
UPDATE ohlcv_candles SET version = 0 WHERE version IS NULL;
UPDATE paper_trading_orders SET version = 0 WHERE version IS NULL;
UPDATE paper_trading_portfolio SET version = 0 WHERE version IS NULL;
UPDATE paper_trading_portfolio_snapshots SET version = 0 WHERE version IS NULL;
UPDATE pdf_extractions SET version = 0 WHERE version IS NULL;
UPDATE positions SET version = 0 WHERE version IS NULL;
UPDATE sentiment_accuracy SET version = 0 WHERE version IS NULL;
UPDATE sentiment_results SET version = 0 WHERE version IS NULL;
UPDATE signals SET version = 0 WHERE version IS NULL;
UPDATE stocks SET version = 0 WHERE version IS NULL;
UPDATE trade_labels SET version = 0 WHERE version IS NULL;
UPDATE trades SET version = 0 WHERE version IS NULL;
UPDATE watchlist SET version = 0 WHERE version IS NULL;
