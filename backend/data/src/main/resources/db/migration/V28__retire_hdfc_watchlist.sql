-- HDFC Ltd was delisted after its merger and must not be ingested as an active instrument.
UPDATE watchlist
SET is_active = FALSE
WHERE symbol = 'HDFC';
