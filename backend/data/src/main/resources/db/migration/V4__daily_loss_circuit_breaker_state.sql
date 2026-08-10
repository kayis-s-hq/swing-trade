CREATE TABLE IF NOT EXISTS daily_loss_circuit_breaker_state (
    id              BIGSERIAL PRIMARY KEY,
    circuit_open    BOOLEAN NOT NULL DEFAULT FALSE,
    circuit_opened_at TIMESTAMP,
    loss_at_open    NUMERIC(15,2),
    last_reset_date DATE NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE daily_loss_circuit_breaker_state
    ADD CONSTRAINT uq_single_row CHECK (id = 1);
