-- Rows created before V46 did not have first_seen_at. Their original
-- persistence timestamp is the strongest available evidence of first sighting.
-- Keep the backfill conservative: rows without created_at remain ineligible for
-- historical point-in-time reconstruction.
UPDATE news_articles
SET first_seen_at = created_at
WHERE first_seen_at IS NULL
  AND created_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_news_articles_symbol_published_first_seen
    ON news_articles(symbol, published_at, first_seen_at);
