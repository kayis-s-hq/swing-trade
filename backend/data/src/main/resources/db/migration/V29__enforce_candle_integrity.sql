-- Remove the known Saturday artifact only for currently active NSE instruments.
DELETE FROM ohlcv_candles c
WHERE c.date = DATE '2026-08-15'
  AND EXISTS (
      SELECT 1 FROM watchlist w
      WHERE w.symbol = c.symbol AND w.exchange = 'NSE' AND w.is_active = TRUE
  );

-- Exact duplicates are safe to collapse. Conflicting values are intentionally left for
-- operator review; the constraint below must fail loudly until they are resolved.
DELETE FROM ohlcv_candles a
USING ohlcv_candles b
WHERE a.symbol = b.symbol AND a.date = b.date
  AND (a.created_at > b.created_at
       OR (a.created_at IS NULL AND b.created_at IS NOT NULL)
       OR (a.created_at IS NOT DISTINCT FROM b.created_at AND a.id > b.id))
  AND a.open_price IS NOT DISTINCT FROM b.open_price
  AND a.high_price IS NOT DISTINCT FROM b.high_price
  AND a.low_price IS NOT DISTINCT FROM b.low_price
  AND a.close_price IS NOT DISTINCT FROM b.close_price
  AND a.volume IS NOT DISTINCT FROM b.volume
  AND a.adj_close_price IS NOT DISTINCT FROM b.adj_close_price;

ALTER TABLE ohlcv_candles
    ADD CONSTRAINT uq_ohlcv_candles_symbol_date UNIQUE (symbol, date);
