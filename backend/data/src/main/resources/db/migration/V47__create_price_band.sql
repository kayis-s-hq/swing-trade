CREATE TABLE IF NOT EXISTS price_band (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    band_date DATE NOT NULL,
    lower_limit NUMERIC(15,4) NOT NULL,
    upper_limit NUMERIC(15,4) NOT NULL,
    CONSTRAINT uq_price_band_symbol_date UNIQUE (symbol, band_date),
    CONSTRAINT ck_price_band_limits_positive CHECK (lower_limit > 0 AND upper_limit > 0),
    CONSTRAINT ck_price_band_limits_ordered CHECK (lower_limit < upper_limit)
);
CREATE INDEX IF NOT EXISTS idx_price_band_symbol_date ON price_band(symbol, band_date);
