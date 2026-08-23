-- V25: Add @Version optimistic locking columns to all entity tables
-- Required by Hibernate @Version field added to 20 JPA entities

ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE daily_loss_circuit_breaker_state ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE fyers_symbol_master ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE job_run_stages ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE job_runs ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE news_items ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE nse_holidays ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE ohlcv_candles ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE paper_trading_orders ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE paper_trading_portfolio ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE paper_trading_portfolio_snapshots ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE pdf_extractions ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE positions ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE sentiment_accuracy ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE sentiment_results ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE signals ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE trade_labels ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE trades ADD COLUMN IF NOT EXISTS version integer;
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS version integer;