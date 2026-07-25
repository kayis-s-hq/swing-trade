-- V18__add_news_articles_table.sql
-- Persist news articles from multiple Indian market sources

CREATE TABLE IF NOT EXISTS news_articles (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL,
    source      VARCHAR(50) NOT NULL,
    title       TEXT NOT NULL,
    link        TEXT,
    summary     TEXT,
    published_at TIMESTAMPTZ,
    raw_content TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_news_articles_symbol ON news_articles(symbol);
CREATE INDEX IF NOT EXISTS idx_news_articles_source ON news_articles(source);
CREATE INDEX IF NOT EXISTS idx_news_articles_published ON news_articles(published_at);