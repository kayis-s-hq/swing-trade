CREATE TABLE IF NOT EXISTS kill_switch (
    id          BIGINT PRIMARY KEY DEFAULT 1,
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_at  TIMESTAMP,
    reason      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO kill_switch (id, active, enabled_at, reason, created_at, updated_at)
VALUES (1, FALSE, NULL, NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;