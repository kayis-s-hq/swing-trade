-- V41 is byte-identical to V35__sentiment_evaluation_audit.sql (both created this table under
-- the same description - a pre-existing duplicate-migration bug on main, unrelated to this
-- plan). CREATE TABLE IF NOT EXISTS makes it a no-op wherever V35 already created the table,
-- so a fresh database no longer fails migrating through V41. This changes V41's checksum:
-- any environment that already applied the original V41 needs `flyway repair` (or equivalent)
-- run against it before its next startup, or Flyway's validate-on-migrate will fail there.
CREATE TABLE IF NOT EXISTS sentiment_evaluation_audits (
    id BIGSERIAL PRIMARY KEY,
    trigger VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed_count INTEGER NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    error_message TEXT
);
