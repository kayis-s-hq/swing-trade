package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for trading operations.
 * Provides endpoints for creating, managing, and closing trading positions.
 */
@RestController
@RequestMapping("/api/trades")
public class TradingController {

    private static final Logger logger = LoggerFactory.getLogger(TradingController.class);

    @Autowired
    private com.swingtrade.api.service.PositionService positionService;

    @Autowired
    private com.swingtrade.api.service.PerformanceService performanceService;

    /**
     * Create a new trading position.
     *
     * @param request Trade request with position details
     * @return Created position response
     */
    @PostMapping
    public ResponseEntity<PositionResponse> createPosition(
            @Valid @RequestBody TradeRequest request
    ) {
        logger.info("Creating new position for symbol: {}", request.getSymbol());

        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        PositionResponse position = positionService.createPosition(request);
        return ResponseEntity.ok(position);
    }

    /**
     * Get all open positions.
     *
     * @return List of open positions
     */
    @GetMapping
    public ResponseEntity<List<PositionResponse>> getOpenPositions() {
        logger.debug("Fetching open positions");
        List<PositionResponse> positions = positionService.getOpenPositions();
        return ResponseEntity.ok(positions);
    }

    /**
     * Get a specific position by symbol.
     *
     * @param symbol Stock symbol
     * @return Position response
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable String symbol) {
        logger.debug("Fetching position for symbol: {}", symbol);
        PositionResponse position = positionService.getPositionBySymbol(symbol);

        if (position == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(position);
    }

    /**
     * Close an existing position.
     *
     * @param symbol Stock symbol
     * @param request Close position request
     * @return Closed position response
     */
    @PostMapping("/{symbol}/close")
    public ResponseEntity<PositionResponse> closePosition(
            @PathVariable String symbol,
            @Valid @RequestBody(required = false) ClosePositionRequest request
    ) {
        logger.info("Closing position for symbol: {}", symbol);
        PositionResponse position = positionService.closePosition(symbol,
                request != null ? request.getExitReason() : null);

        if (position == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(position);
    }

    /**
     * Get trade history for a symbol.
     *
     * @param symbol Stock symbol
     * @return List of trade responses
     */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<TradeResponse>> getTradeHistory(@PathVariable String symbol) {
        logger.debug("Fetching trade history for symbol: {}", symbol);
        List<TradeResponse> trades = positionService.getTradeHistory(symbol);
        return ResponseEntity.ok(trades);
    }

    /**
     * Get overall portfolio performance.
     *
     * @return Performance response
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceResponse> getPortfolioPerformance() {
        logger.debug("Fetching portfolio performance");
        PerformanceResponse performance = performanceService.getPortfolioPerformance();
        return ResponseEntity.ok(performance);
    }

    /**
     * Get risk summary for current positions.
     *
     * @return Risk summary
     */
    @GetMapping("/risk-summary")
    public ResponseEntity<com.swingtrade.api.dto.RiskSummary> getRiskSummary() {
        logger.debug("Fetching risk summary");
        com.swingtrade.api.dto.RiskSummary riskSummary = positionService.getRiskSummary();
        return ResponseEntity.ok(riskSummary);
    }
}
