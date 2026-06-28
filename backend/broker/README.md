# Broker Module

The broker module provides paper trading functionality for the swing trading system. It includes all the necessary components to manage trades, positions, and portfolio performance.

## Features

### Order Management
- Supports different order types (MARKET, LIMIT, STOP, STOP_LIMIT)
- Order lifecycle management (PENDING, ACCEPTED, EXECUTING, FILLED, CANCELLED, EXPIRED)

### Paper Trading Engine
- Simulates order execution at next day open
- Real-time position tracking with daily SL/target monitoring
- P&L calculation for both open and closed positions

### Position Management
- Maximum 5 concurrent positions enforcement
- 20% capital per position constraint
- Stop Loss and Take Profit monitoring

### Portfolio Management
- Capital tracking and allocation
- Real-time profit/loss calculations
- Position state management

## Architecture

### Core Components

1. **Model Layer**
   - `Order`: Represents trading orders
   - `Position`: Tracks individual holdings
   - `Portfolio`: Manages overall capital and positions

2. **Service Layer**
   - `PaperTradingServiceImpl`: Implements broker service functionality
   - `PaperTradeEngine`: Core engine for paper trading operations

3. **Configuration**
   - `BrokerConfig`: Spring Boot configuration for the module

## Usage

The broker module integrates with the broader swing trading system to provide:
- Paper trading simulation capabilities
- Position tracking and management
- Risk controls (position limits and capital allocation)
- Performance reporting

## Constraints

- Maximum 5 concurrent positions
- Maximum 20% of total capital per position
- All operations are simulated for paper trading purposes
