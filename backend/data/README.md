# Data Module

This module provides data ingestion services for the swing trading system, integrating with the Upstox REST API to fetch financial market data.

## Features Implemented

### 1. Upstox REST Client
- Spring WebClient-based client for interacting with Upstox API
- Configuration properties support using `@ConfigurationProperties`
- Retry logic for handling transient API failures
- Proper error handling for authentication and HTTP errors

### 2. Data Ingestion Services
- **Scheduled Jobs**: Auto-ingestion at 16:30 IST on weekdays using `@Scheduled`
- **Backfilling**: Support for fetching 3 years of historical data for Nifty 500 stocks
- **Error Handling**: Comprehensive exception handling throughout the data pipeline

### 3. Data Quality Validation
- Missing candle detection functionality
- Anomaly flagging system
- Reporting framework for data quality issues

## Module Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/swingtrade/data/
│   │       ├── config/              # Configuration classes
│   │       │   ├── UpstoxConfig.java     # Upstox API configuration
│   │       │   └── SchedulingConfig.java # Scheduling configuration
│   │       ├── client/              # REST client implementations
│   │       │   └── UpstoxRestClient.java # Upstox API client
│   │       ├── model/               # Data models
│   │       │   └── CandleData.java       # Candlestick data model
│   │       ├── service/             # Business logic services
│   │       │   └── DataIngestionService.java # Main ingestion service
│   │       └── Application.java     # Module entry point
│   └── resources/
│       └── application.yml          # Configuration properties
└── test/
    └── java/
        └── com/swingtrade/data/
            └── service/
                └── DataIngestionServiceTest.java # Unit tests
```

## Configuration Properties

```yaml
upstox:
  client-id: ${UPSTOX_CLIENT_ID:demo_client_id}
  client-secret: ${UPSTOX_CLIENT_SECRET:demo_client_secret}
  redirect-uri: ${UPSTOX_REDIRECT_URI:http://localhost:8080/callback}
  api-url: ${UPSTOX_API_URL:https://api.upstox.com/v2}
  access-token: ${UPSTOX_ACCESS_TOKEN:demo_access_token}
  max-retries: 3
  retry-delay-millis: 1000
```

## Key Components

### 1. UpstoxConfig
Configuration class for Upstox API settings with proper Spring Boot property binding.

### 2. UpstoxRestClient
WebClient implementation for Upstox REST API integration with:
- Proper HTTP headers
- Authentication handling
- Retry mechanisms
- Error handling

### 3. DataIngestionService
Main service providing:
- Scheduled auto-ingestion at 16:30 IST on weekdays
- Backfilling capability for historical data
- Data quality validation
- Comprehensive logging

### 4. Data Quality Validator
Detects missing candles and flags anomalies with detailed reporting.

## Usage

The services are configured to run automatically via Spring's scheduling mechanism. The auto-ingestion job runs daily at 16:30 IST on weekdays, fetching data for the Nifty 500 stocks over the past 3 years.

The service implements Spring Boot best practices:
- Constructor injection for dependencies
- @ConfigurationProperties for externalized configuration
- Proper exception handling
- Comprehensive logging
