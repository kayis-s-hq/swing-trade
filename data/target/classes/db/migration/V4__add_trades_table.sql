-- =============================================================================
-- Swing Trade Database - Flyway Migration V4
-- =============================================================================
-- Description: Create comprehensive trades table with position tracking
--              and complete audit trail
-- Author: Swing Trade Team
-- Date: 2026-03-07
-- =============================================================================

-- =============================================================================
-- Trades Table - Enhanced
-- =============================================================================
-- Create comprehensive trades table for tracking all trading activity
-- This includes both paper and real trading execution details

DROP TABLE IF EXISTS trades CASCADE;

CREATE TABLE trades (
    id SERIAL NOT NULL,
    trade_id VARCHAR(50) UNIQUE, -- Exchange trade ID (for real trades) or system-generated ID
    symbol VARCHAR(10) NOT NULL,
    trade_type VARCHAR(10) NOT NULL, -- BUY, SELL, COVER, SQUARE_OFF
    side VARCHAR(10) NOT NULL, -- LONG, SHORT
    quantity NUMERIC(18,4) NOT NULL,
    price NUMERIC(15,4) NOT NULL,
    stop_loss_price NUMERIC(15,4),
    target_price NUMERIC(15,4),
    order_type VARCHAR(20) NOT NULL, -- MARKET, LIMIT, STOPLOSS, STOPLOSS_MARKET
    product_type VARCHAR(20), -- INTRADAY, DELIVERY, MIS
    trigger_price NUMERIC(15,4),
    disclosed_quantity INTEGER,
    exchange_order_id VARCHAR(50), -- Exchange-assigned order ID
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, OPEN, FILLED, REJECTED, CANCELLED, PARTIALLY_FILLED
    execution_type VARCHAR(20), -- NEW, MODIFY, CANCEL, DISCLOSE
    fees NUMERIC(15,4) DEFAULT 0,
    brokerage NUMERIC(15,4) DEFAULT 0,
    exchange_charges NUMERIC(15,4) DEFAULT 0,
    stt NUMERIC(15,4) DEFAULT 0,
    stamp_duty NUMERIC(15,4) DEFAULT 0,
    misc_charges NUMERIC(15,4) DEFAULT 0,
    total_fees NUMERIC(15,4) DEFAULT 0,

    -- Position Tracking
    position_id INTEGER REFERENCES positions(id) ON DELETE SET NULL,
    position_type VARCHAR(10), -- LONG, SHORT (for position context)

    -- Signal & Strategy Context
    signal_id INTEGER REFERENCES signals(id) ON DELETE SET NULL,
    signal_type VARCHAR(20),
    strategy_name VARCHAR(100),
    strategy_params JSONB, -- Strategy configuration at trade time

    -- LLM & Sentiment Context
    sentiment_score DECIMAL(5,2),
    sentiment_label VARCHAR(20),
    llm_analysis TEXT,

    -- Trade Details
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_timestamp TIMESTAMP WITH TIME ZONE,
    fill_timestamp TIMESTAMP WITH TIME ZONE,
    cancel_timestamp TIMESTAMP WITH TIME ZONE,
    reject_timestamp TIMESTAMP WITH TIME ZONE,

    -- Error & Status Details
    rejection_reason TEXT,
    cancel_reason TEXT,
    broker_response JSONB, -- Raw broker API response

    -- Audit Fields
    source VARCHAR(50) DEFAULT 'SYSTEM', -- SYSTEM, MANUAL, API, SCHEDULED
    notes TEXT,
    metadata JSONB, -- Additional metadata

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create comprehensive indexes for trades table
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_trade_type ON trades(trade_type);
CREATE INDEX IF NOT EXISTS idx_trades_side ON trades(side);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
CREATE INDEX IF NOT EXISTS idx_trades_symbol_status ON trades(symbol, status);
CREATE INDEX IF NOT EXISTS idx_trades_symbol_type ON trades(symbol, trade_type);
CREATE INDEX IF NOT EXISTS idx_trades_symbol_timestamp ON trades(symbol, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_trades_timestamp ON trades(timestamp);
CREATE INDEX IF NOT EXISTS idx_trades_position_id ON trades(position_id);
CREATE INDEX IF NOT EXISTS idx_trades_signal_id ON trades(signal_id);
CREATE INDEX IF NOT EXISTS idx_trades_strategy ON trades(strategy_name);
CREATE INDEX IF NOT EXISTS idx_trades_source ON trades(source);
CREATE INDEX IF NOT EXISTS idx_trades_trade_id ON trades(trade_id);
CREATE INDEX IF NOT EXISTS idx_trades_exchange_order_id ON trades(exchange_order_id) WHERE exchange_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_order_timestamp ON trades(order_timestamp) WHERE order_timestamp IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_fill_timestamp ON trades(fill_timestamp) WHERE fill_timestamp IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_created_at ON trades(created_at);
CREATE INDEX IF NOT EXISTS idx_trades_updated_at ON trades(updated_at);

-- GIN index for JSONB fields
CREATE INDEX IF NOT EXISTS idx_trades_strategy_params_gin ON trades USING GIN(strategy_params);
CREATE INDEX IF NOT EXISTS idx_trades_broker_response_gin ON trades USING GIN(broker_response);
CREATE INDEX IF NOT EXISTS idx_trades_metadata_gin ON trades USING GIN(metadata);

-- Partial indexes for common queries
CREATE INDEX IF NOT EXISTS idx_trades_active ON trades(symbol, timestamp DESC)
    WHERE status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_trades_filled ON trades(symbol, timestamp DESC)
    WHERE status = 'FILLED';

CREATE INDEX IF NOT EXISTS idx_trades_buy ON trades(symbol, timestamp DESC)
    WHERE trade_type = 'BUY' AND status = 'FILLED';

CREATE INDEX IF NOT EXISTS idx_trades_sell ON trades(symbol, timestamp DESC)
    WHERE trade_type = 'SELL' AND status = 'FILLED';

-- Add comments
COMMENT ON TABLE trades IS 'Complete audit trail of all trading activity';
COMMENT ON COLUMN trades.trade_id IS 'Unique trade identifier (exchange or system)';
COMMENT ON COLUMN trades.symbol IS 'Stock/symbol identifier';
COMMENT ON COLUMN trades.trade_type IS 'Type of trade: BUY, SELL, COVER, SQUARE_OFF';
COMMENT ON COLUMN trades.side IS 'Position direction: LONG, SHORT';
COMMENT ON COLUMN trades.quantity IS 'Number of shares/units';
COMMENT ON COLUMN trades.price IS 'Execution price per unit';
COMMENT ON COLUMN trades.stop_loss_price IS 'Stop loss price for the trade';
COMMENT ON COLUMN trades.target_price IS 'Target price for the trade';
COMMENT ON COLUMN trades.order_type IS 'Order type: MARKET, LIMIT, STOPLOSS, STOPLOSS_MARKET';
COMMENT ON COLUMN trades.product_type IS 'Product type: INTRADAY, DELIVERY, MIS';
COMMENT ON COLUMN trades.trigger_price IS 'Trigger price for stop orders';
COMMENT ON trades.dislosed_quantity IS 'Disclosed quantity for display';
COMMENT ON COLUMN trades.exchange_order_id IS 'Exchange-assigned order ID';
COMMENT ON COLUMN trades.status IS 'Trade status: PENDING, OPEN, FILLED, REJECTED, CANCELLED, PARTIALLY_FILLED';
COMMENT ON COLUMN trades.execution_type IS 'Execution type: NEW, MODIFY, CANCEL, DISCLOSE';
COMMENT ON COLUMN trades.fees IS 'Total fees including brokerage and charges';
COMMENT ON COLUMN trades.brokerage IS 'Brokerage charges only';
COMMENT ON COLUMN trades.exchange_charges IS 'Exchange transaction charges';
COMMENT ON COLUMN trades.stt IS 'Securities Transaction Tax';
COMMENT ON COLUMN trades.stamp_duty IS 'Stamp duty charges';
COMMENT ON COLUMN trades.misc_charges IS 'Miscellaneous charges (SEBI, DP, GST)';
COMMENT ON COLUMN trades.total_fees IS 'Sum of all charges';
COMMENT ON COLUMN trades.position_id IS 'Linked position identifier';
COMMENT ON COLUMN trades.signal_id IS 'Linked signal identifier';
COMMENT ON COLUMN trades.signal_type IS 'Signal type that triggered the trade';
COMMENT ON COLUMN trades.strategy_name IS 'Name of the trading strategy';
COMMENT ON COLUMN trades.strategy_params IS 'Strategy parameters at execution time';
COMMENT ON COLUMN trades.sentiment_score IS 'Sentiment score from LLM analysis';
COMMENT ON COLUMN trades.sentiment_label IS 'Sentiment label: POSITIVE, NEGATIVE, NEUTRAL';
COMMENT ON COLUMN trades.llm_analysis IS 'LLM sentiment analysis text';
COMMENT ON COLUMN trades.timestamp IS 'Trade execution timestamp';
COMMENT ON COLUMN trades.order_timestamp IS 'Order submission timestamp';
COMMENT ON COLUMN trades.fill_timestamp IS 'Order fill timestamp';
COMMENT ON COLUMN trades.cancel_timestamp IS 'Order cancel timestamp';
COMMENT ON COLUMN trades.reject_timestamp IS 'Order reject timestamp';
COMMENT ON COLUMN trades.rejection_reason IS 'Reason for trade rejection';
COMMENT ON COLUMN trades.cancel_reason IS 'Reason for trade cancellation';
COMMENT ON COLUMN trades.broker_response IS 'Raw broker API response';
COMMENT ON COLUMN trades.source IS 'Trade source: SYSTEM, MANUAL, API, SCHEDULED';
COMMENT ON COLUMN trades.notes IS 'Additional notes about the trade';
COMMENT ON COLUMN trades.metadata IS 'Additional metadata as JSON';

-- =============================================================================
-- Positions Table - Enhanced
-- =============================================================================
-- Enhance positions table with comprehensive tracking

DROP TABLE IF EXISTS positions CASCADE;

CREATE TABLE positions (
    id SERIAL NOT NULL,
    position_id VARCHAR(50) UNIQUE, -- Exchange position ID
    symbol VARCHAR(10) NOT NULL,
    position_type VARCHAR(10) NOT NULL, -- LONG, SHORT
    quantity NUMERIC(18,4) NOT NULL,
    filled_quantity NUMERIC(18,4) DEFAULT 0,
    avg_price NUMERIC(15,4) NOT NULL,
    current_price NUMERIC(15,4),
    day_entry_price NUMERIC(15,4),
    entry_price NUMERIC(15,4) NOT NULL,
    exit_price NUMERIC(15,4),
    target_price NUMERIC(15,4),
    stop_loss_price NUMERIC(15,4),
    unrealized_pnl NUMERIC(15,4) DEFAULT 0,
    realized_pnl NUMERIC(15,4) DEFAULT 0,
    day_pnl NUMERIC(15,4) DEFAULT 0,
    total_pnl NUMERIC(15,4) DEFAULT 0,

    -- Entry & Exit Details
    entry_signal_id INTEGER REFERENCES signals(id) ON DELETE SET NULL,
    entry_timestamp TIMESTAMP WITH TIME ZONE,
    exit_signal_id INTEGER REFERENCES signals(id) ON DELETE SET NULL,
    exit_timestamp TIMESTAMP WITH TIME ZONE,
    close_timestamp TIMESTAMP WITH TIME ZONE,

    -- Position Status
    status VARCHAR(20) DEFAULT 'OPEN', -- OPEN, CLOSED, STOPPED, ADJUSTED
    exit_reason VARCHAR(100), -- TARGET, STOPLOSS, MANUAL, TIME, SYSTEM
    is_intraday BOOLEAN DEFAULT FALSE,
    is_hedged BOOLEAN DEFAULT FALSE,

    -- Strategy Context
    strategy_name VARCHAR(100),
    trade_count INTEGER DEFAULT 0,

    -- Metadata
    source VARCHAR(50) DEFAULT 'SYSTEM',
    notes TEXT,
    metadata JSONB,

    -- Audit Fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create indexes for positions table
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);
CREATE INDEX IF NOT EXISTS idx_positions_status ON positions(status);
CREATE INDEX IF NOT EXISTS idx_positions_symbol_status ON positions(symbol, status);
CREATE INDEX IF NOT EXISTS idx_positions_type ON positions(position_type);
CREATE INDEX IF NOT EXISTS idx_positions_symbol_type ON positions(symbol, position_type);
CREATE INDEX IF NOT EXISTS idx_positions_entry_timestamp ON positions(entry_timestamp) WHERE entry_timestamp IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_positions_exit_timestamp ON positions(exit_timestamp) WHERE exit_timestamp IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_positions_created_at ON positions(created_at);
CREATE INDEX IF NOT EXISTS idx_positions_updated_at ON positions(updated_at);
CREATE INDEX IF NOT EXISTS idx_positions_entry_signal ON positions(entry_signal_id);
CREATE INDEX IF NOT EXISTS idx_positions_exit_signal ON positions(exit_signal_id);
CREATE INDEX IF NOT EXISTS idx_positions_strategy ON positions(strategy_name);
CREATE INDEX IF NOT EXISTS idx_positions_unrealized_pnl ON positions(unrealized_pnl DESC) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_positions_symbol_open ON positions(symbol)
    WHERE status = 'OPEN';

-- GIN index for metadata
CREATE INDEX IF NOT EXISTS idx_positions_metadata_gin ON positions USING GIN(metadata);

-- Add comments
COMMENT ON TABLE positions IS 'Active and historical position tracking';
COMMENT ON COLUMN positions.position_id IS 'Exchange-assigned position ID';
COMMENT ON COLUMN positions.symbol IS 'Stock/symbol identifier';
COMMENT ON COLUMN positions.position_type IS 'Position direction: LONG, SHORT';
COMMENT ON COLUMN positions.quantity IS 'Total position quantity';
COMMENT ON COLUMN positions.filled_quantity IS 'Filled quantity';
COMMENT ON COLUMN positions.avg_price IS 'Average entry price';
COMMENT ON COLUMN positions.current_price IS 'Current market price';
COMMENT ON COLUMN positions.day_entry_price IS 'Today''s entry price';
COMMENT ON COLUMN positions.entry_price IS 'Primary entry price';
COMMENT ON COLUMN positions.exit_price IS 'Exit price';
COMMENT ON COLUMN positions.target_price IS 'Target price set';
COMMENT ON COLUMN positions.stop_loss_price IS 'Stop loss price set';
COMMENT ON COLUMN positions.unrealized_pnl IS 'Current unrealized profit/loss';
COMMENT ON COLUMN positions.realized_pnl IS 'Realized profit/loss from closed position';
COMMENT ON COLUMN positions.day_pnl IS 'P&L for today''s trading';
COMMENT ON COLUMN positions.total_pnl IS 'Total P&L';
COMMENT ON COLUMN positions.entry_signal_id IS 'Signal that triggered entry';
COMMENT ON COLUMN positions.entry_timestamp IS 'Position entry timestamp';
COMMENT ON COLUMN positions.exit_signal_id IS 'Signal that triggered exit';
COMMENT ON COLUMN positions.exit_timestamp IS 'Position exit timestamp';
COMMENT ON COLUMN positions.close_timestamp IS 'Position fully closed timestamp';
COMMENT ON COLUMN positions.status IS 'Position status: OPEN, CLOSED, STOPPED, ADJUSTED';
COMMENT ON COLUMN positions.exit_reason IS 'Reason for position exit';
COMMENT ON COLUMN positions.is_intraday IS 'Whether position was closed intraday';
COMMENT ON COLUMN positions.is_hedged IS 'Whether position is hedged';
COMMENT ON COLUMN positions.strategy_name IS 'Strategy name';
COMMENT ON COLUMN positions.trade_count IS 'Number of trades in this position';
COMMENT ON COLUMN positions.source IS 'Position source: SYSTEM, MANUAL, API';
COMMENT ON COLUMN positions.notes IS 'Additional notes';
COMMENT ON COLUMN positions.metadata IS 'Additional metadata as JSON';

-- =============================================================================
-- Positions History Table
-- =============================================================================
-- Track position state changes for audit trail

DROP TABLE IF EXISTS positions_history CASCADE;

CREATE TABLE positions_history (
    id SERIAL NOT NULL,
    position_id INTEGER NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    position_type VARCHAR(10) NOT NULL,
    quantity NUMERIC(18,4) NOT NULL,
    avg_price NUMERIC(15,4),
    current_price NUMERIC(15,4),
    unrealized_pnl NUMERIC(15,4),
    total_pnl NUMERIC(15,4),
    status VARCHAR(20),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_type VARCHAR(50), -- ENTRY, EXIT, ADD, REDUCE, SL_CHANGE, TARGET_CHANGE, MARK_TO_MARKET
    change_details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_history_position
        FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE
);

-- Create indexes for history table
CREATE INDEX IF NOT EXISTS idx_pos_history_position_id ON positions_history(position_id);
CREATE INDEX IF NOT EXISTS idx_pos_history_symbol_timestamp ON positions_history(symbol, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pos_history_timestamp ON positions_history(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pos_history_change_type ON positions_history(change_type);
CREATE INDEX IF NOT EXISTS idx_pos_history_created_at ON positions_history(created_at);
CREATE INDEX IF NOT EXISTS idx_pos_history_status ON positions_history(status);

-- GIN index for change details
CREATE INDEX IF NOT EXISTS idx_pos_history_change_details_gin ON positions_history USING GIN(change_details);

COMMENT ON TABLE positions_history IS 'Historical record of all position state changes';
COMMENT ON COLUMN positions_history.position_id IS 'Original position identifier';
COMMENT ON COLUMN positions_history.change_type IS 'Type of change: ENTRY, EXIT, ADD, REDUCE, SL_CHANGE, TARGET_CHANGE, MARK_TO_MARKET';
COMMENT ON COLUMN positions_history.change_details IS 'Details of the change as JSON';

-- =============================================================================
-- Order Tracking Table
-- =============================================================================
-- Track all orders placed with the broker

DROP TABLE IF EXISTS orders CASCADE;

CREATE TABLE orders (
    id SERIAL NOT NULL,
    order_id VARCHAR(50) NOT NULL,
    exchange_order_id VARCHAR(50) UNIQUE, -- Exchange-assigned order ID
    trade_id VARCHAR(50), -- Link to trades table if filled
    symbol VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL, -- BUY, SELL
    quantity NUMERIC(18,4) NOT NULL,
    price NUMERIC(15,4),
    trigger_price NUMERIC(15,4),
    order_type VARCHAR(20) NOT NULL, -- MARKET, LIMIT, STOPLOSS, STOPLOSS_MARKET
    product_type VARCHAR(20), -- INTRADAY, DELIVERY, MIS
    exchange VARCHAR(20), -- NSE, BSE
    segment VARCHAR(20), -- EQ, F&O, CURRENCY
    status VARCHAR(20) NOT NULL, -- PENDING, OPEN, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED, EXPIRED
    order_source VARCHAR(20) DEFAULT 'API', -- API, MANUAL, SYSTEM
    parent_order_id VARCHAR(50), -- For bracket/cover orders
    tag VARCHAR(50), -- Order tracking tag
    strategy_name VARCHAR(100),
    signal_id INTEGER REFERENCES signals(id) ON DELETE SET NULL,
    position_id INTEGER REFERENCES positions(id) ON DELETE SET NULL,

    -- Order Timing
    order_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exchange_timestamp TIMESTAMP WITH TIME ZONE,
    last_update_time TIMESTAMP WITH TIME ZONE,

    -- Order Response
    broker_order_status VARCHAR(50),
    reject_reason TEXT,
    broker_response JSONB,

    -- Audit
    source VARCHAR(50) DEFAULT 'SYSTEM',
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_order_id UNIQUE (order_id)
);

-- Create indexes for orders table
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_exchange_order_id ON orders(exchange_order_id) WHERE exchange_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_order_id ON orders(order_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_side ON orders(side);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_symbol_status ON orders(symbol, status);
CREATE INDEX IF NOT EXISTS idx_orders_symbol_side ON orders(symbol, side);
CREATE INDEX IF NOT EXISTS idx_orders_symbol_timestamp ON orders(symbol, order_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_orders_timestamp ON orders(order_timestamp);
CREATE INDEX IF NOT EXISTS idx_orders_position_id ON orders(position_id);
CREATE INDEX IF NOT EXISTS idx_orders_signal_id ON orders(signal_id);
CREATE INDEX IF NOT EXISTS idx_orders_strategy ON orders(strategy_name);
CREATE INDEX IF NOT EXISTS idx_orders_parent_order_id ON orders(parent_order_id) WHERE parent_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_updated_at ON orders(updated_at);
CREATE INDEX IF NOT EXISTS idx_orders_trade_id ON orders(trade_id) WHERE trade_id IS NOT NULL;

-- GIN indexes for JSONB fields
CREATE INDEX IF NOT EXISTS idx_orders_broker_response_gin ON orders USING GIN(broker_response);
CREATE INDEX IF NOT EXISTS idx_orders_metadata_gin ON orders USING GIN(metadata);

-- Partial indexes
CREATE INDEX IF NOT EXISTS idx_orders_open ON orders(symbol, order_timestamp DESC)
    WHERE status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_orders_filled ON orders(symbol, order_timestamp DESC)
    WHERE status = 'FILLED';

COMMENT ON TABLE orders IS 'Complete order lifecycle tracking';
COMMENT ON COLUMN orders.order_id IS 'System-generated order ID';
COMMENT ON COLUMN orders.exchange_order_id IS 'Exchange-assigned order ID';
COMMENT ON COLUMN trades.trade_id IS 'Linked trade ID (if filled)';
COMMENT ON COLUMN orders.symbol IS 'Stock/symbol identifier';
COMMENT ON COLUMN orders.side IS 'BUY or SELL';
COMMENT ON COLUMN orders.quantity IS 'Order quantity';
COMMENT ON COLUMN orders.price IS 'Order price (for LIMIT orders)';
COMMENT ON COLUMN orders.trigger_price IS 'Trigger price (for stop orders)';
COMMENT ON COLUMN orders.order_type IS 'Order type: MARKET, LIMIT, STOPLOSS, STOPLOSS_MARKET';
COMMENT ON COLUMN orders.product_type IS 'Product type: INTRADAY, DELIVERY, MIS';
COMMENT ON COLUMN orders.exchange IS 'Exchange: NSE, BSE';
COMMENT ON COLUMN orders.segment IS 'Segment: EQ, F&O, CURRENCY';
COMMENT ON COLUMN orders.status IS 'Order status: PENDING, OPEN, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED, EXPIRED';
COMMENT ON COLUMN orders.order_source IS 'Source of order: API, MANUAL, SYSTEM';
COMMENT ON COLUMN orders.parent_order_id IS 'Parent order ID (for bracket/cover orders)';
COMMENT ON COLUMN orders.tag IS 'Order tracking tag';
COMMENT ON COLUMN orders.strategy_name IS 'Strategy that generated the order';
COMMENT ON COLUMN orders.signal_id IS 'Linked signal ID';
COMMENT ON COLUMN orders.position_id IS 'Linked position ID';
COMMENT ON COLUMN orders.order_timestamp IS 'Order creation timestamp';
COMMENT ON COLUMN orders.exchange_timestamp IS 'Exchange receipt timestamp';
COMMENT ON COLUMN orders.last_update_time IS 'Last status update timestamp';
COMMENT ON COLUMN orders.broker_order_status IS 'Broker-reported status';
COMMENT ON COLUMN orders.reject_reason IS 'Reason for rejection';
COMMENT ON COLUMN orders.broker_response IS 'Raw broker response';
COMMENT ON COLUMN orders.source IS 'Order source: SYSTEM, MANUAL, API';
COMMENT ON COLUMN orders.notes IS 'Additional notes';
COMMENT ON COLUMN orders.metadata IS 'Additional metadata';

-- =============================================================================
-- P&L Summary Table (Daily)
-- =============================================================================
-- Daily P&L summary for reporting

DROP TABLE IF EXISTS pnl_summary CASCADE;

CREATE TABLE pnl_summary (
    id SERIAL NOT NULL,
    date DATE NOT NULL,
    symbol VARCHAR(10), -- NULL for total portfolio

    -- Position Summary
    opening_positions INTEGER DEFAULT 0,
    closing_positions INTEGER DEFAULT 0,
    new_positions INTEGER DEFAULT 0,
    adjusted_positions INTEGER DEFAULT 0,

    -- Trading Summary
    total_trades INTEGER DEFAULT 0,
    buy_trades INTEGER DEFAULT 0,
    sell_trades INTEGER DEFAULT 0,
    buy_value NUMERIC(18,4) DEFAULT 0,
    sell_value NUMERIC(18,4) DEFAULT 0,

    -- P&L Summary
    realized_pnl NUMERIC(15,4) DEFAULT 0,
    unrealized_pnl NUMERIC(15,4) DEFAULT 0,
    day_pnl NUMERIC(15,4) DEFAULT 0,
    total_pnl NUMERIC(15,4) DEFAULT 0,

    -- Fees
    total_brokerage NUMERIC(15,4) DEFAULT 0,
    total_exchange_charges NUMERIC(15,4) DEFAULT 0,
    total_stt NUMERIC(15,4) DEFAULT 0,
    total_stamp_duty NUMERIC(15,4) DEFAULT 0,
    total_misc_charges NUMERIC(15,4) DEFAULT 0,
    total_fees NUMERIC(15,4) DEFAULT 0,

    -- Metrics
    win_count INTEGER DEFAULT 0,
    loss_count INTEGER DEFAULT 0,
    win_rate DECIMAL(5,2),
    avg_win NUMERIC(15,4),
    avg_loss NUMERIC(15,4),
    profit_factor DECIMAL(10,4),

    -- Strategy Breakdown (JSON)
    pnl_by_strategy JSONB,

    -- Metadata
    source VARCHAR(50) DEFAULT 'SYSTEM',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_pnl_summary_date_symbol ON pnl_summary(date, COALESCE(symbol, 'TOTAL'));
CREATE INDEX IF NOT EXISTS idx_pnl_summary_date ON pnl_summary(date DESC);
CREATE INDEX IF NOT EXISTS idx_pnl_summary_symbol ON pnl_summary(symbol);
CREATE INDEX IF NOT EXISTS idx_pnl_summary_created_at ON pnl_summary(created_at);

-- Partial index for daily summary
CREATE INDEX IF NOT EXISTS idx_pnl_summary_daily ON pnl_summary(date) WHERE symbol IS NULL;

COMMENT ON TABLE pnl_summary IS 'Daily P&L summary for reporting and analytics';
COMMENT ON COLUMN pnl_summary.date IS 'Trading date';
COMMENT ON COLUMN pnl_summary.symbol IS 'Stock symbol (NULL for portfolio summary)';
COMMENT ON COLUMN pnl_summary.opening_positions IS 'Positions opened at start of day';
COMMENT ON COLUMN pnl_summary.closing_positions IS 'Positions closed during day';
COMMENT ON COLUMN pnl_summary.new_positions IS 'New positions opened';
COMMENT ON COLUMN pnl_summary.adjusted_positions IS 'Positions adjusted';
COMMENT ON COLUMN pnl_summary.total_trades IS 'Total trades executed';
COMMENT ON COLUMN pnl_summary.buy_trades IS 'Buy trades executed';
COMMENT ON COLUMN pnl_summary.sell_trades IS 'Sell trades executed';
COMMENT ON COLUMN pnl_summary.buy_value IS 'Total buy value';
COMMENT ON COLUMN pnl_summary.sell_value IS 'Total sell value';
COMMENT ON COLUMN pnl_summary.realized_pnl IS 'Realized P&L from closed positions';
COMMENT ON COLUMN pnl_summary.unrealized_pnl IS 'Unrealized P&L from open positions';
COMMENT ON COLUMN pnl_summary.day_pnl IS 'Day''s total P&L';
COMMENT ON COLUMN pnl_summary.total_pnl IS 'Total P&L including carried positions';
COMMENT ON COLUMN pnl_summary.total_brokerage IS 'Total brokerage charges';
COMMENT ON COLUMN pnl_summary.total_exchange_charges IS 'Total exchange charges';
COMMENT ON COLUMN pnl_summary.total_stt IS 'Total STT';
COMMENT ON COLUMN pnl_summary.total_stamp_duty IS 'Total stamp duty';
COMMENT ON COLUMN pnl_summary.total_misc_charges IS 'Total miscellaneous charges';
COMMENT ON COLUMN pnl_summary.total_fees IS 'Sum of all fees';
COMMENT ON COLUMN pnl_summary.win_count IS 'Number of winning trades';
COMMENT ON COLUMN pnl_summary.loss_count IS 'Number of losing trades';
COMMENT ON COLUMN pnl_summary.win_rate IS 'Win rate percentage';
COMMENT ON COLUMN pnl_summary.avg_win IS 'Average winning trade P&L';
COMMENT ON COLUMN pnl_summary.avg_loss IS 'Average losing trade P&L';
COMMENT ON COLUMN pnl_summary.profit_factor IS 'Gross profit / Gross loss';
COMMENT ON COLUMN pnl_summary.pnl_by_strategy IS 'P&L breakdown by strategy as JSON';

-- =============================================================================
-- Trigger Functions for Updated At
-- =============================================================================
-- Ensure updated_at is refreshed on any row update

DROP TRIGGER IF EXISTS update_trades_updated_at ON trades;
CREATE TRIGGER update_trades_updated_at
    BEFORE UPDATE ON trades
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_positions_updated_at ON positions;
CREATE TRIGGER update_positions_updated_at
    BEFORE UPDATE ON positions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_orders_updated_at ON orders;
CREATE TRIGGER update_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_pnl_summary_updated_at ON pnl_summary;
CREATE TRIGGER update_pnl_summary_updated_at
    BEFORE UPDATE ON pnl_summary
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Data Integrity Constraints
-- =============================================================================
-- Add constraints for data validation

-- Trades: quantity must be positive
ALTER TABLE trades
ADD CONSTRAINT chk_trades_quantity CHECK (quantity > 0);

-- Trades: price must be positive
ALTER TABLE trades
ADD CONSTRAINT chk_trades_price CHECK (price > 0);

-- Positions: quantity must be positive
ALTER TABLE positions
ADD CONSTRAINT chk_positions_quantity CHECK (quantity > 0);

-- Positions: avg_price must be positive
ALTER TABLE positions
ADD CONSTRAINT chk_positions_avg_price CHECK (avg_price > 0);

-- Orders: quantity must be positive
ALTER TABLE orders
ADD CONSTRAINT chk_orders_quantity CHECK (quantity > 0);

-- Orders: price must be positive when set
ALTER TABLE orders
ADD CONSTRAINT chk_orders_price CHECK (price IS NULL OR price > 0);

-- Valid trade types
ALTER TABLE trades
ADD CONSTRAINT chk_trades_trade_type CHECK (trade_type IN ('BUY', 'SELL', 'COVER', 'SQUARE_OFF'));

-- Valid position types
ALTER TABLE positions
ADD CONSTRAINT chk_positions_type CHECK (position_type IN ('LONG', 'SHORT'));

-- Valid trade sides
ALTER TABLE trades
ADD CONSTRAINT chk_trades_side CHECK (side IN ('LONG', 'SHORT'));

-- Valid order sides
ALTER TABLE orders
ADD CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL'));

-- Valid statuses
ALTER TABLE trades
ADD CONSTRAINT chk_trades_status CHECK (status IN ('PENDING', 'OPEN', 'FILLED', 'REJECTED', 'CANCELLED', 'PARTIALLY_FILLED'));

ALTER TABLE positions
ADD CONSTRAINT chk_positions_status CHECK (status IN ('OPEN', 'CLOSED', 'STOPPED', 'ADJUSTED', 'MANUAL'));

ALTER TABLE orders
ADD CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

-- =============================================================================
-- View for Active Positions with Current Values
-- =============================================================================
-- Create a view for easy access to current position data

CREATE OR REPLACE VIEW v_active_positions AS
SELECT
    p.id,
    p.position_id,
    p.symbol,
    p.position_type,
    p.quantity,
    p.filled_quantity,
    p.avg_price,
    p.current_price,
    p.entry_price,
    p.target_price,
    p.stop_loss_price,
    p.unrealized_pnl,
    p.realized_pnl,
    p.day_pnl,
    p.total_pnl,
    p.entry_signal_id,
    p.entry_timestamp,
    p.status,
    p.exit_reason,
    p.is_intraday,
    p.is_hedged,
    p.strategy_name,
    p.trade_count,
    p.source,
    p.created_at,
    p.updated_at,
    (p.quantity * COALESCE(p.current_price, 0)) AS market_value,
    (p.quantity * (COALESCE(p.current_price, p.avg_price) - p.avg_price) * p.quantity) AS total_unrealized_pnl
FROM positions p
WHERE p.status = 'OPEN';

COMMENT ON VIEW v_active_positions IS 'View of all active positions with calculated values';

-- =============================================================================
-- View for Position Summary
-- =============================================================================
CREATE OR REPLACE VIEW v_position_summary AS
SELECT
    symbol,
    COUNT(*) FILTER (WHERE status = 'OPEN') AS open_count,
    COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_count,
    SUM(quantity) FILTER (WHERE status = 'OPEN') AS total_open_quantity,
    SUM(unrealized_pnl) FILTER (WHERE status = 'OPEN') AS total_unrealized_pnl,
    SUM(realized_pnl) FILTER (WHERE status = 'CLOSED') AS total_realized_pnl,
    SUM(total_pnl) FILTER (WHERE status = 'CLOSED') AS total_pnl_closed
FROM positions
GROUP BY symbol;

COMMENT ON VIEW v_position_summary IS 'Summary of positions by symbol';

-- =============================================================================
-- Migration Complete
-- =============================================================================
-- This migration creates comprehensive tables for:
-- 1. Trades - complete audit trail of all trades
-- 2. Positions - active and historical position tracking
-- 3. Positions History - state change audit trail
-- 4. Orders - complete order lifecycle tracking
-- 5. P&L Summary - daily P&L reporting
--
-- All tables include:
-- - Comprehensive indexes for performance
-- - Constraints for data integrity
-- - Triggers for audit timestamps
-- - Comments for documentation
-- - GIN indexes for JSONB fields
-- - Partial indexes for common queries
