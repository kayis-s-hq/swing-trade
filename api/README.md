# Swing Trade API Module

This module provides REST endpoints for the swing trading system with the following functionality:

## Endpoints

### GET /api/signals/latest
Returns today's trading signals in JSON format.

### GET /api/positions
Returns open paper trading positions.

### GET /api/performance
Returns backtest and paper trading performance statistics.

### POST /api/scan
Triggers a manual scan for trading opportunities.

## Features

- Spring Boot REST API with proper Javadoc documentation
- Spring Actuator integration for monitoring
- Micrometer metrics tracking
- Type-safe POJOs for API responses

## Configuration

The API module requires the following dependencies:
- spring-boot-starter-web
- spring-boot-starter-actuator
- micrometer-core
- micrometer-registry-prometheus

## Usage

Run the application with:
```
mvn spring-boot:run
```

Endpoints will be available at:
- http://localhost:8080/api/signals/latest
- http://localhost:8080/api/positions
- http://localhost:8080/api/performance
- http://localhost:8080/api/scan (POST)
