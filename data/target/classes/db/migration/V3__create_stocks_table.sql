-- =============================================================================
-- Swing Trade Database - Flyway Migration V3
-- =============================================================================
-- Description: Create and enhance stocks table with comprehensive indexes
--              and data for Indian equities
-- Author: Swing Trade Team
-- Date: 2026-03-07
-- =============================================================================

-- =============================================================================
-- Stocks Table Enhancement
-- =============================================================================
-- Create enhanced stocks table with additional fields for better filtering
-- and trading capabilities

-- Drop and recreate table for clean migration (adjust based on existing data)
DROP TABLE IF EXISTS stocks CASCADE;

CREATE TABLE stocks (
    id SERIAL NOT NULL,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    exchange VARCHAR(50), -- NSE, BSE
    segment VARCHAR(50),  -- EQ, F&O,Currency
    sector VARCHAR(100),
    industry VARCHAR(100),

    -- Corporate Details
    isin_equity VARCHAR(12),
    isin_debt VARCHAR(12),
    isin_dr VARCHAR(12),

    -- Market Data
    market_cap BIGINT, -- Market capitalization in INR
    face_value NUMERIC(15,4),
    book_value NUMERIC(15,4),
    pe_ratio DECIMAL(10,4),
    pb_ratio DECIMAL(10,4),
    div_yield DECIMAL(8,4),
    eps_trailing DECIMAL(10,4),

    -- Trading Data
    listing_date DATE,
    expiry_date DATE, -- For derivatives
    lot_size INTEGER, -- For F&O
    min_lot_quantity INTEGER,

    -- Performance Metrics
    week_52_high NUMERIC(15,4),
    week_52_low NUMERIC(15,4),
    week_52_high_date DATE,
    week_52_low_date DATE,

    -- Status Flags
    is_active BOOLEAN DEFAULT TRUE,
    is_tradable BOOLEAN DEFAULT TRUE,
    is_derivatives BOOLEAN DEFAULT FALSE,

    -- Metadata
    source VARCHAR(50), -- NSE, BSE, MANUAL
    data_verified BOOLEAN DEFAULT FALSE,
    last_data_update TIMESTAMP WITH TIME ZONE,

    -- Audit Fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create comprehensive indexes for stocks table
CREATE UNIQUE INDEX IF NOT EXISTS idx_stocks_symbol ON stocks(symbol);
CREATE INDEX IF NOT EXISTS idx_stocks_name ON stocks(name);
CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks(exchange);
CREATE INDEX IF NOT EXISTS idx_stocks_exchange_symbol ON stocks(exchange, symbol);
CREATE INDEX IF NOT EXISTS idx_stocks_sector ON stocks(sector);
CREATE INDEX IF NOT EXISTS idx_stocks_industry ON stocks(industry);
CREATE INDEX IF NOT EXISTS idx_stocks_sector_industry ON stocks(sector, industry);
CREATE INDEX IF NOT EXISTS idx_stocks_is_active ON stocks(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stocks_is_tradable ON stocks(is_tradable) WHERE is_tradable = TRUE;
CREATE INDEX IF NOT EXISTS idx_stocks_market_cap ON stocks(market_cap DESC) WHERE market_cap IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stocks_pe_ratio ON stocks(pe_ratio) WHERE pe_ratio IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stocks_listing_date ON stocks(listing_date);
CREATE INDEX IF NOT EXISTS idx_stocks_expiry_date ON stocks(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stocks_created_at ON stocks(created_at);
CREATE INDEX IF NOT EXISTS idx_stocks_updated_at ON stocks(updated_at);
CREATE INDEX IF NOT EXISTS idx_stocks_last_data_update ON stocks(last_data_update) WHERE last_data_update IS NOT NULL;

-- Create GIN index for sector search (if we add tags)
-- CREATE INDEX IF NOT EXISTS idx_stocks_sector_gin ON stocks USING GIN(sector);

-- Add comments
COMMENT ON TABLE stocks IS 'Master table of tradable stocks and securities';
COMMENT ON COLUMN stocks.symbol IS 'Unique stock symbol (NSE/BSE format)';
COMMENT ON COLUMN stocks.name IS 'Full company name';
COMMENT ON COLUMN stocks.exchange IS 'Exchange listing: NSE, BSE';
COMMENT ON COLUMN stocks.segment IS 'Trading segment: EQ (Equity), F&O, Currency';
COMMENT ON COLUMN stocks.sector IS 'Industry sector';
COMMENT ON COLUMN stocks.industry IS 'Specific industry';
COMMENT ON COLUMN stocks.isin_equity IS 'ISIN code for equity shares';
COMMENT ON COLUMN stocks.market_cap IS 'Market capitalization in Indian Rupees';
COMMENT ON COLUMN stocks.face_value IS 'Face value per share';
COMMENT ON COLUMN stocks.book_value IS 'Book value per share';
COMMENT ON COLUMN stocks.pe_ratio IS 'Price to Earnings ratio';
COMMENT ON COLUMN stocks.pb_ratio IS 'Price to Book ratio';
COMMENT ON COLUMN stocks.div_yield IS 'Dividend yield percentage';
COMMENT ON COLUMN stocks.eps_trailing IS 'Earnings per share (trailing)';
COMMENT ON COLUMN stocks.listing_date IS 'Stock listing date on exchange';
COMMENT ON COLUMN stocks.expiry_date IS 'Derivatives expiry date';
COMMENT ON COLUMN stocks.lot_size IS 'Minimum trading lot size for F&O';
COMMENT ON COLUMN stocks.week_52_high IS '52-week high price';
COMMENT ON COLUMN stocks.week_52_low IS '52-week low price';
COMMENT ON COLUMN stocks.is_active IS 'Whether stock is actively traded';
COMMENT ON COLUMN stocks.is_tradable IS 'Whether stock can be traded';
COMMENT ON COLUMN stocks.is_derivatives IS 'Whether stock has F&O segment';
COMMENT ON COLUMN stocks.source IS 'Data source: NSE, BSE, MANUAL';
COMMENT ON COLUMN stocks.data_verified IS 'Whether data has been verified';

-- =============================================================================
-- Stocks Sector Master Table
-- =============================================================================
-- Create a reference table for sectors and industries

CREATE TABLE IF NOT EXISTS stock_sectors (
    id SERIAL PRIMARY KEY,
    sector_name VARCHAR(100) NOT NULL UNIQUE,
    sector_code VARCHAR(20),
    industry_name VARCHAR(100),
    industry_code VARCHAR(20),
    description TEXT,
    nse_category VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sectors_name ON stock_sectors(sector_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sectors_code ON stock_sectors(sector_code);
CREATE INDEX IF NOT EXISTS idx_sectors_industry ON stock_sectors(industry_name);
CREATE INDEX IF NOT EXISTS idx_sectors_nse_category ON stock_sectors(nse_category);

COMMENT ON TABLE stock_sectors IS 'Reference table for stock sectors and industries';

-- =============================================================================
-- Stocks Watchlist Table
-- =============================================================================
-- Create watchlist for user preferences

CREATE TABLE IF NOT EXISTS stock_watchlist (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL, -- Could be UUID, email, or system identifier
    symbol VARCHAR(10) NOT NULL,
    added_reason VARCHAR(200),
    priority INTEGER DEFAULT 0, -- 0=normal, 1=high, 2=critical
    alerts_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_watchlist_symbol
        FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE,
    CONSTRAINT uq_watchlist_user_symbol UNIQUE (user_id, symbol)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user ON stock_watchlist(user_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_symbol ON stock_watchlist(symbol);
CREATE INDEX IF NOT EXISTS idx_watchlist_priority ON stock_watchlist(priority DESC);
CREATE INDEX IF NOT EXISTS idx_watchlist_created_at ON stock_watchlist(created_at);

COMMENT ON TABLE stock_watchlist IS 'User watchlists for tracking stocks of interest';

-- =============================================================================
-- Populate Stocks Data for Indian Equities
-- =============================================================================
-- Insert commonly traded NSE stocks

INSERT INTO stocks (symbol, name, exchange, segment, sector, industry, is_active, is_tradable, is_derivatives, source)
VALUES
    -- NIFTY 50 Stocks
    ('RELIANCE', 'Reliance Industries Limited', 'NSE', 'EQ', 'Oil & Gas', 'Refineries & Marketing', TRUE, TRUE, TRUE, 'NSE'),
    ('TCS', 'Tata Consultancy Services Limited', 'NSE', 'EQ', 'Information Technology', 'IT Services & Consulting', TRUE, TRUE, TRUE, 'NSE'),
    ('HDFCBANK', 'HDFC Bank Limited', 'NSE', 'EQ', 'Financial Services', 'Private Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('INFY', 'Infosys Limited', 'NSE', 'EQ', 'Information Technology', 'IT Services & Consulting', TRUE, TRUE, TRUE, 'NSE'),
    ('ICICIBANK', 'ICICI Bank Limited', 'NSE', 'EQ', 'Financial Services', 'Private Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('HINDUNILVR', 'Hindustan Unilever Limited', 'NSE', 'EQ', 'FMCG', 'Personal Care', TRUE, TRUE, TRUE, 'NSE'),
    ('ITC', 'ITC Limited', 'NSE', 'EQ', 'FMCG', 'Diversified', TRUE, TRUE, TRUE, 'NSE'),
    ('SBIN', 'State Bank of India', 'NSE', 'EQ', 'Financial Services', 'Public Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('BHARTIARTL', 'Bharti Airtel Limited', 'NSE', 'EQ', 'Telecommunications', 'Telecom Services', TRUE, TRUE, TRUE, 'NSE'),
    ('KOTAKBANK', 'Kotak Mahindra Bank Limited', 'NSE', 'EQ', 'Financial Services', 'Private Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('LT', 'Larsen & Toubro Limited', 'NSE', 'EQ', 'Capital Goods', 'Construction & Engineering', TRUE, TRUE, TRUE, 'NSE'),
    ('ASIANPAINT', 'Asian Paints Limited', 'NSE', 'EQ', 'Consumer Durables', 'Paints', TRUE, TRUE, TRUE, 'NSE'),
    ('AXISBANK', 'Axis Bank Limited', 'NSE', 'EQ', 'Financial Services', 'Private Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('BAJFINANCE', 'Bajaj Finance Limited', 'NSE', 'EQ', 'Financial Services', 'NBFC', TRUE, TRUE, TRUE, 'NSE'),
    ('MARUTI', 'Maruti Suzuki India Limited', 'NSE', 'EQ', 'Automobile', 'Passenger Cars & Utility Vehicles', TRUE, TRUE, TRUE, 'NSE'),
    ('TITAN', 'Titan Company Limited', 'NSE', 'EQ', 'Consumer Durables', 'Gems, Jewellery & Watches', TRUE, TRUE, TRUE, 'NSE'),
    ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'NSE', 'EQ', 'Healthcare', 'Pharmaceuticals', TRUE, TRUE, TRUE, 'NSE'),
    ('TATAMOTORS', 'Tata Motors Limited', 'NSE', 'EQ', 'Automobile', 'Commercial Vehicles & Passenger Cars', TRUE, TRUE, TRUE, 'NSE'),
    ('WIPRO', 'Wipro Limited', 'NSE', 'EQ', 'Information Technology', 'IT Services & Consulting', TRUE, TRUE, TRUE, 'NSE'),
    ('ULTRACEMCO', 'UltraTech Cement Limited', 'NSE', 'EQ', 'Capital Goods', 'Cement', TRUE, TRUE, TRUE, 'NSE'),
    ('HCLTECH', 'HCL Technologies Limited', 'NSE', 'EQ', 'Information Technology', 'IT Services & Consulting', TRUE, TRUE, TRUE, 'NSE'),
    ('ADANIENT', 'Adani Enterprises Limited', 'NSE', 'EQ', 'Metals & Mining', 'Diversified', TRUE, TRUE, TRUE, 'NSE'),
    ('BAJAJFINSV', 'Bajaj Finserv Limited', 'NSE', 'EQ', 'Financial Services', 'Diversified Financial Services', TRUE, TRUE, TRUE, 'NSE'),
    ('BRITANNIA', 'Britannia Industries Limited', 'NSE', 'EQ', 'FMCG', 'Food Products', TRUE, TRUE, TRUE, 'NSE'),
    ('TATASTEEL', 'Tata Steel Limited', 'NSE', 'EQ', 'Metals & Mining', 'Steel', TRUE, TRUE, TRUE, 'NSE'),
    ('NESTLEIND', 'Nestle India Limited', 'NSE', 'EQ', 'FMCG', 'Food Products', TRUE, TRUE, TRUE, 'NSE'),
    ('ONGC', 'Oil and Natural Gas Corporation Limited', 'NSE', 'EQ', 'Oil & Gas', 'Oil & Gas Exploration & Production', TRUE, TRUE, TRUE, 'NSE'),
    ('POWERGRID', 'Power Grid Corporation of India Limited', 'NSE', 'EQ', 'Power', 'Power Transmission', TRUE, TRUE, TRUE, 'NSE'),
    ('NTPC', 'NTPC Limited', 'NSE', 'EQ', 'Power', 'Power Generation', TRUE, TRUE, TRUE, 'NSE'),
    ('TECHM', 'Tech Mahindra Limited', 'NSE', 'EQ', 'Information Technology', 'IT Services & Consulting', TRUE, TRUE, TRUE, 'NSE'),
    ('JSWSTEEL', 'JSW Steel Limited', 'NSE', 'EQ', 'Metals & Mining', 'Steel', TRUE, TRUE, TRUE, 'NSE'),
    ('HEROMOTOCO', 'Hero MotoCorp Limited', 'NSE', 'EQ', 'Automobile', 'Two Wheelers', TRUE, TRUE, TRUE, 'NSE'),
    ('HDFC', 'HDFC Limited', 'NSE', 'EQ', 'Financial Services', 'Housing Finance', TRUE, TRUE, TRUE, 'NSE'),
    ('DIVISLAB', 'Divi''s Laboratories Limited', 'NSE', 'EQ', 'Healthcare', 'Pharmaceuticals', TRUE, TRUE, TRUE, 'NSE'),
    ('DRREDDY', 'Dr. Reddy''s Laboratories Limited', 'NSE', 'EQ', 'Healthcare', 'Pharmaceuticals', TRUE, TRUE, TRUE, 'NSE'),
    ('CIPLA', 'Cipla Limited', 'NSE', 'EQ', 'Healthcare', 'Pharmaceuticals', TRUE, TRUE, TRUE, 'NSE'),
    ('SHRIRAMFIN', 'Shriram Finance Limited', 'NSE', 'EQ', 'Financial Services', 'NBFC', TRUE, TRUE, TRUE, 'NSE'),
    ('GRASIM', 'Grasim Industries Limited', 'NSE', 'EQ', 'Building Materials', 'Cement', TRUE, TRUE, TRUE, 'NSE'),
    ('APOLLOHOSP', 'Apollo Hospitals Enterprise Limited', 'NSE', 'EQ', 'Healthcare', 'Hospitals & Diagnostics', TRUE, TRUE, TRUE, 'NSE'),
    ('ADANIPORTS', 'Adani Ports and Special Economic Zone Limited', 'NSE', 'EQ', 'Services', 'Ports & Port Operations', TRUE, TRUE, TRUE, 'NSE'),
    ('M&M', 'Mahindra & Mahindra Limited', 'NSE', 'EQ', 'Automobile', 'Tractors & Utility Vehicles', TRUE, TRUE, TRUE, 'NSE'),
    ('EICHERMOT', 'Eicher Motors Limited', 'NSE', 'EQ', 'Automobile', 'Two Wheelers', TRUE, TRUE, TRUE, 'NSE'),
    ('BPCL', 'Bharat Petroleum Corporation Limited', 'NSE', 'EQ', 'Oil & Gas', 'Oil & Gas Refining & Marketing', TRUE, TRUE, TRUE, 'NSE'),
    ('SBILIFE', 'SBI Life Insurance Company Limited', 'NSE', 'EQ', 'Financial Services', 'Insurance', TRUE, TRUE, TRUE, 'NSE'),
    ('ADANIGREEN', 'Adani Wilmar Limited', 'NSE', 'EQ', 'FMCG', 'Food Products', TRUE, TRUE, TRUE, 'NSE'),
    ('HINDALCO', 'Hindalco Industries Limited', 'NSE', 'EQ', 'Metals & Mining', 'Aluminium', TRUE, TRUE, TRUE, 'NSE'),
    ('AUROPHARMA', 'Aurobindo Pharma Limited', 'NSE', 'EQ', 'Healthcare', 'Pharmaceuticals', TRUE, TRUE, TRUE, 'NSE'),
    ('TATAPOWER', 'Tata Power Company Limited', 'NSE', 'EQ', 'Power', 'Power Generation & Distribution', TRUE, TRUE, TRUE, 'NSE'),
    ('INDUSINDBK', 'IndusInd Bank Limited', 'NSE', 'EQ', 'Financial Services', 'Private Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('BANKBARODA', 'Bank of Baroda', 'NSE', 'EQ', 'Financial Services', 'Public Sector Bank', TRUE, TRUE, TRUE, 'NSE'),
    ('TRENT', 'Trent Limited', 'NSE', 'EQ', 'Consumer Discretionary', 'Apparel Retail', TRUE, TRUE, TRUE, 'NSE'),

    -- Bank Nifty Constituents
    ('BANKNIFTY', 'NIFTY Bank Index', 'NSE', 'INDEX', 'Financial Services', 'Banking Index', TRUE, TRUE, FALSE, 'NSE'),

    -- NIFTY 50 Index
    ('NIFTY', 'NIFTY 50 Index', 'NSE', 'INDEX', 'Market Index', 'Broad Market Index', TRUE, TRUE, FALSE, 'NSE')
ON CONFLICT (symbol) DO UPDATE SET
    name = EXCLUDED.name,
    exchange = EXCLUDED.exchange,
    sector = EXCLUDED.sector,
    industry = EXCLUDED.industry,
    updated_at = CURRENT_TIMESTAMP;

-- =============================================================================
-- Insert Sector Data
-- =============================================================================
INSERT INTO stock_sectors (sector_name, industry_name, nse_category)
VALUES
    ('Oil & Gas', 'Refineries & Marketing', 'Oil Gas'),
    ('Oil & Gas', 'Oil & Gas Exploration & Production', 'Oil Gas'),
    ('Oil & Gas', 'Oil & Gas Refining & Marketing', 'Oil Gas'),
    ('Information Technology', 'IT Services & Consulting', 'IT'),
    ('Financial Services', 'Private Sector Bank', 'Finance'),
    ('Financial Services', 'Public Sector Bank', 'Finance'),
    ('Financial Services', 'NBFC', 'Finance'),
    ('Financial Services', 'NBFC - Housing', 'Finance'),
    ('Financial Services', 'NBFC - Others', 'Finance'),
    ('Financial Services', 'NBFC - Loans', 'Finance'),
    ('Financial Services', 'Housing Finance', 'Finance'),
    ('Financial Services', 'Insurance', 'Finance'),
    ('Financial Services', 'Diversified Financial Services', 'Finance'),
    ('FMCG', 'Personal Care', 'FMCG'),
    ('FMCG', 'Food Products', 'FMCG'),
    ('FMCG', 'Diversified', 'FMCG'),
    ('Automobile', 'Passenger Cars & Utility Vehicles', 'Auto'),
    ('Automobile', 'Commercial Vehicles & Passenger Cars', 'Auto'),
    ('Automobile', 'Two Wheelers', 'Auto'),
    ('Automobile', 'Tractors & Utility Vehicles', 'Auto'),
    ('Capital Goods', 'Construction & Engineering', 'Capital Goods'),
    ('Capital Goods', 'Cement', 'Capital Goods'),
    ('Capital Goods', 'Steel', 'Capital Goods'),
    ('Consumer Durables', 'Paints', 'Consumption'),
    ('Consumer Durables', 'Gems, Jewellery & Watches', 'Consumption'),
    ('Healthcare', 'Pharmaceuticals', 'Healthcare'),
    ('Healthcare', 'Hospitals & Diagnostics', 'Healthcare'),
    ('Services', 'Ports & Port Operations', 'Services'),
    ('Power', 'Power Generation & Distribution', 'Power'),
    ('Power', 'Power Generation', 'Power'),
    ('Power', 'Power Transmission', 'Power'),
    ('Metals & Mining', 'Steel', 'Metals'),
    ('Metals & Mining', 'Aluminium', 'Metals'),
    ('Metals & Mining', 'Diversified', 'Metals'),
    ('Telecommunications', 'Telecom Services', 'Telecom'),
    ('Building Materials', 'Cement', 'Capital Goods'),
    ('Consumer Discretionary', 'Apparel Retail', 'Consumption')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- Add Triggers for Updated At
-- =============================================================================
-- Create trigger function if not exists
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Attach trigger to stocks table
DROP TRIGGER IF EXISTS update_stocks_updated_at ON stocks;
CREATE TRIGGER update_stocks_updated_at
    BEFORE UPDATE ON stocks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Attach trigger to stock_sectors table
DROP TRIGGER IF EXISTS update_stock_sectors_updated_at ON stock_sectors;
CREATE TRIGGER update_stock_sectors_updated_at
    BEFORE UPDATE ON stock_sectors
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Attach trigger to stock_watchlist table
DROP TRIGGER IF EXISTS update_stock_watchlist_updated_at ON stock_watchlist;
CREATE TRIGGER update_stock_watchlist_updated_at
    BEFORE UPDATE ON stock_watchlist
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Add Constraints for Data Validation
-- =============================================================================
-- Ensure PE ratio is positive when set
ALTER TABLE stocks
ADD CONSTRAINT chk_pe_ratio CHECK (pe_ratio IS NULL OR pe_ratio > 0);

-- Ensure PB ratio is positive when set
ALTER TABLE stocks
ADD CONSTRAINT chk_pb_ratio CHECK (pb_ratio IS NULL OR pb_ratio > 0);

-- Ensure face_value is positive when set
ALTER TABLE stocks
ADD CONSTRAINT chk_face_value CHECK (face_value IS NULL OR face_value > 0);

-- Ensure book_value is positive when set
ALTER TABLE stocks
ADD CONSTRAINT chk_book_value CHECK (book_value IS NULL OR book_value > 0);

-- Ensure quantity values are positive
ALTER TABLE stocks
ADD CONSTRAINT chk_lot_size CHECK (lot_size IS NULL OR lot_size > 0);

-- =============================================================================
-- Migration Complete
-- =============================================================================
-- This migration:
-- 1. Creates an enhanced stocks table with comprehensive fields
-- 2. Adds multiple indexes for efficient querying
-- 3. Creates reference tables for sectors and industries
-- 4. Implements watchlist functionality
-- 5. Populates data for major NSE stocks
-- 6. Adds triggers and constraints for data integrity
