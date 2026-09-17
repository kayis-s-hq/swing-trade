ALTER TABLE gate_effectiveness_audit
    ADD COLUMN IF NOT EXISTS strategy VARCHAR(30) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE gate_effectiveness_audit
    DROP CONSTRAINT IF EXISTS uq_gate_effectiveness_symbol_date_gate;

ALTER TABLE gate_effectiveness_audit
    ADD CONSTRAINT uq_gate_effectiveness_symbol_date_gate_strategy
    UNIQUE (symbol, signal_date, gate_name, strategy);
