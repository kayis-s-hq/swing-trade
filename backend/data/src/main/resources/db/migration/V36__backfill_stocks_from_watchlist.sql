-- Keep the stocks parent table in sync with watchlist rows created by the
-- consolidated V1 seed and by older deployments.
INSERT INTO stocks (symbol, name, exchange, added_on)
SELECT symbol,
       COALESCE(NULLIF(name, ''), symbol),
       COALESCE(NULLIF(exchange, ''), 'NSE'),
       CURRENT_DATE
FROM watchlist
ON CONFLICT (symbol) DO NOTHING;
