ALTER TABLE signals ADD COLUMN strategy VARCHAR(30) NOT NULL DEFAULT 'DEFAULT';

CREATE INDEX idx_signals_symbol_date_strategy ON signals(symbol, date, strategy);
