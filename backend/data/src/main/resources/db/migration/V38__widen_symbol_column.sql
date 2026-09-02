-- Widen symbol columns from VARCHAR(10) to VARCHAR(20) to accommodate longer
-- NSE symbols (e.g. suffixed series/derivative symbols) without truncation.
-- Safe widening ALTER — no data rewrite or cast required in Postgres.
ALTER TABLE stocks ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE ohlcv_candles ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE signals ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE positions ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE trades ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE sentiment_results ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE watchlist ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE news_items ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE pdf_extractions ALTER COLUMN symbol TYPE VARCHAR(20);
