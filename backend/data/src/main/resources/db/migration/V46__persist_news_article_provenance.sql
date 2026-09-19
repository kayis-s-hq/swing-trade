ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS article_key VARCHAR(64);

ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMPTZ;

DROP INDEX IF EXISTS ux_news_articles_article_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_news_articles_symbol_article_key
    ON news_articles(symbol, article_key)
    WHERE article_key IS NOT NULL;

ALTER TABLE sentiment_results
    ADD COLUMN IF NOT EXISTS article_ids BIGINT[];

-- Existing rows cannot be safely linked to evidence because the previous schema
-- did not retain article identities. New sentiment rows populate this array.
