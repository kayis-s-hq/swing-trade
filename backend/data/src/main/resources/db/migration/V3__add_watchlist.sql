-- Watchlist table for tracking stocks to monitor
CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name TEXT,
    exchange VARCHAR(5) DEFAULT 'NSE',
    is_active BOOLEAN DEFAULT TRUE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP,
    candle_count INTEGER DEFAULT 0
);

-- Index for active lookups
CREATE INDEX IF NOT EXISTS idx_watchlist_active ON watchlist(is_active);

-- Seed Nifty 50 top 15 stocks
INSERT INTO watchlist (symbol, name, exchange) VALUES
    ('RELIANCE', 'Reliance Industries Limited', 'NSE'),
    ('TCS', 'Tata Consultancy Services Limited', 'NSE'),
    ('INFY', 'Infosys Limited', 'NSE'),
    ('HDFCBANK', 'HDFC Bank Limited', 'NSE'),
    ('ICICIBANK', 'ICICI Bank Limited', 'NSE'),
    ('HDFC', 'Housing Development Finance Corporation Limited', 'NSE'),
    ('SBIN', 'State Bank of India', 'NSE'),
    ('BHARTIARTL', 'Bharti Airtel Limited', 'NSE'),
    ('ITC', 'ITC Limited', 'NSE'),
    ('KOTAKBANK', 'Kotak Mahindra Bank Limited', 'NSE'),
    ('LT', 'Larsen & Toubro Limited', 'NSE'),
    ('AXISBANK', 'Axis Bank Limited', 'NSE'),
    ('MARUTI', 'Maruti Suzuki India Limited', 'NSE'),
    ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'NSE'),
    ('WIPRO', 'Wipro Limited', 'NSE')
ON CONFLICT (symbol) DO NOTHING;
