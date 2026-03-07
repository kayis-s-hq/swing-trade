# Swing Trade System

A comprehensive swing trading system built with modern Java technologies and microservices architecture.

## Project Overview

The Swing Trade System is a modular trading platform designed for swing trading strategies. It integrates real-time market data, technical analysis, sentiment analysis, and automated trading capabilities. The system is composed of several interconnected modules that work together to provide a complete trading solution.

## Architecture

```
Swing Trade System
├── Core Components
│   ├── Data Layer (Data Module)
│   ├── Strategy Engine (Strategy Module)
│   ├── LLM Integration (LLM Module)
│   └── Broker Layer (Broker Module)
├── API Layer (API Module)
└── Database & Caching
    ├── PostgreSQL with TimescaleDB
    └── Redis
```

### Key Modules

1. **Core Module**: Shared domain models and utilities
2. **Data Module**: Market data ingestion and storage
3. **Strategy Module**: Trading strategy implementation and backtesting
4. **LLM Module**: Natural language processing for sentiment analysis
5. **Broker Module**: Paper trading engine and position management
6. **API Module**: REST endpoints for system interaction

## Technology Stack

- **Language**: Java 11+
- **Build Tool**: Maven
- **Framework**: Spring Boot
- **Database**: PostgreSQL with TimescaleDB extension
- **Caching**: Redis
- **Containerization**: Docker
- **Monitoring**: Prometheus + Grafana (not included in this repo)

## Setup Instructions

### Prerequisites

1. Java 11 or higher
2. Maven 3.6+
3. Docker and Docker Compose
4. IDE (IntelliJ IDEA recommended)

### Environment Setup

#### 1. Database Configuration

The system requires PostgreSQL with TimescaleDB extension and Redis.

```bash
# Start required services using Docker
docker-compose up -d
```

This will start:
- PostgreSQL database (port 5432)
- Redis cache (port 6379)

#### 2. Environment Variables

Create a `.env` file in the root directory:

```bash
# Database Configuration
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=swingtrade_db
POSTGRES_USER=swingtrade_user
POSTGRES_PASSWORD=swingtrade_password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# API Configuration
API_PORT=8080

# LLM Configuration (optional)
OPENAI_API_KEY=your_openai_api_key_here
```

### Build and Run

```bash
# Build the entire project
mvn clean install

# Run individual modules
cd api && mvn spring-boot:run

# Or run the full application using the Spring Boot plugin
mvn spring-boot:run
```

## How to Run the System

### Option 1: Using Docker (Recommended)

```bash
# Start all services
docker-compose up -d

# Build and run the application
mvn clean install
java -jar api/target/api-1.0.0.jar
```

### Option 2: Manual Setup

1. Start PostgreSQL and Redis containers:
```bash
docker-compose up -d
```

2. Configure database schema:
```bash
# Apply database migrations using Flyway
# This is typically handled automatically during application startup
```

3. Build and run the application:
```bash
mvn clean install
java -jar api/target/api-1.0.0.jar
```

### Option 3: Development Mode

```bash
# Import the project into your IDE
# Run the main class: com.swingtrade.api.app.SwingTradeApiApplication
```

## Module Descriptions

### Core Module
- Contains shared domain models and utilities
- Defines common data structures used across the system
- Includes basic utility classes

### Data Module
- **Purpose**: Market data ingestion and storage
- **Key Features**:
  - Real-time market data collection
  - Historical data storage with TimescaleDB
  - Data validation and normalization
- **Components**:
  - `DataIngestionService`: Manages data flow from external sources
  - `UpstoxRestClient`: Integration with Upstox API
  - Database migration scripts (Flyway)

### Strategy Module
- **Purpose**: Trading strategy implementation and backtesting
- **Key Features**:
  - Technical indicator calculations
  - Strategy composition and execution
  - Backtesting framework
- **Components**:
  - `DefaultStrategy`: Main trading strategy implementation
  - `DefaultIndicatorService`: Technical indicators calculation
  - `BacktestEngine`: Backtesting infrastructure

### LLM Module
- **Purpose**: Sentiment analysis and natural language processing
- **Key Features**:
  - News sentiment analysis
  - Technical signal generation from text
  - Integration with LLM APIs
- **Components**:
  - `LangChain4jLlmClient`: LLM integration layer
  - `NewsIngestionService`: News data processing
  - `SentimentAnalysisResult`: Sentiment data structures

### Broker Module
- **Purpose**: Order management and paper trading simulation
- **Key Features**:
  - Portfolio management
  - Order processing and execution
  - Position tracking
- **Components**:
  - `PaperTradingEngine`: Paper trade execution engine
  - `BrokerService`: Core broker functionality
  - Position and order data models

### API Module
- **Purpose**: RESTful interface for system interaction
- **Key Features**:
  - Trading signals endpoint
  - Performance metrics
  - Position management
- **Endpoints**:
  - `GET /api/signals` - Get trading signals
  - `GET /api/performance` - Get performance statistics
  - `GET /api/positions` - Get current positions
  - `POST /api/trade` - Execute trades

## API Endpoints

### Signals Endpoint
```
GET /api/signals
```
Returns trading signals with:
- Stock symbols
- Signal types (Buy/Sell)
- Confidence scores
- Technical indicators

### Performance Endpoint
```
GET /api/performance
```
Returns portfolio performance metrics:
- Total return
- Drawdown statistics
- Win rate
- Profitability metrics

### Positions Endpoint
```
GET /api/positions
```
Returns current open positions:
- Stock symbols
- Quantity held
- Entry price
- Current value
- P&L

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please open an issue in the repository or contact the maintainers.

---
*Built with ❤️ for algorithmic trading*
