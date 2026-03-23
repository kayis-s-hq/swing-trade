package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for trading operations.
 * Provides endpoints for creating, managing, and closing trading positions.
 */
@RestController
@RequestMapping("/api/trades")
public class TradingController {

    private static final Logger logger = LoggerFactory.getLogger(TradingController.class);

    @Autowired
    private com.swingtrade.api.PositionService positionService;

    @Autowired
    private com.swingtrade.api.PerformanceService performanceService;

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

        try {
            if (!request.isValid()) {
                return ResponseEntity.badRequest().body(null);
            }

            PositionResponse position = positionService.createPosition(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(position);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Bad Request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating position: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to create position"));
        }
    }

    /**
     * Get all open positions.
     *
     * @return List of open positions
     */
    @GetMapping
    public ResponseEntity<List<PositionResponse>> getOpenPositions() {
        logger.debug("Fetching open positions");

        try {
            List<PositionResponse> positions = positionService.getOpenPositions();
            return ResponseEntity.ok(positions);

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to fetch positions"));
        }
    }

    /**
     * Get a specific position by symbol.
     *
     * @param request Symbol request
     * @return Position response
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable String symbol) {
        logger.debug("Fetching position for symbol: {}", symbol);

        try {
            PositionResponse position = positionService.getPositionBySymbol(symbol);

            if (position == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(position);

        } catch (Exception e) {
            logger.error("Error fetching position: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to fetch position"));
        }
    }

    /**
     * Close an existing position.
     *
     * @param request Close position request
     * @return Closed position response
     */
    @PostMapping("/{symbol}/close")
    public ResponseEntity<PositionResponse> closePosition(
            @PathVariable String symbol,
            @Valid @RequestBody(required = false) ClosePositionRequest request
    ) {
        logger.info("Closing position for symbol: {}", symbol);

        try {
            PositionResponse position = positionService.closePosition(symbol,
                    request != null ? request.getExitReason() : null);

            if (position == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(position);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Bad Request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error closing position: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to close position"));
        }
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

        try {
            List<TradeResponse> trades = positionService.getTradeHistory(symbol);
            return ResponseEntity.ok(trades);

        } catch (Exception e) {
            logger.error("Error fetching trade history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to fetch trade history"));
        }
    }

    /**
     * Get overall portfolio performance.
     *
     * @return Performance response
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceResponse> getPortfolioPerformance() {
        logger.debug("Fetching portfolio performance");

        try {
            PerformanceResponse performance = performanceService.getPortfolioPerformance();
            return ResponseEntity.ok(performance);

        } catch (Exception e) {
            logger.error("Error fetching performance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to fetch performance"));
        }
    }

    /**
     * Get risk summary for current positions.
     *
     * @return Risk summary
     */
    @GetMapping("/risk-summary")
    public ResponseEntity<RiskSummary> getRiskSummary() {
        logger.debug("Fetching risk summary");

        try {
            RiskSummary riskSummary = positionService.getRiskSummary();
            return ResponseEntity.ok(riskSummary);

        } catch (Exception e) {
            logger.error("Error fetching risk summary: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("Internal Server Error", "Failed to fetch risk summary"));
        }
    }

    /**
     * Build an error response wrapper.
     */
    private PositionResponse buildErrorResponse(String error, String message) {
        PositionResponse response = new PositionResponse();
        response.setEntryReason(error);
        response.setEntryPrice(new com.swingtrade.api.dto.PositionResponse.PositionStatus[]{
                com.swingtrade.api.dto.PositionResponse.PositionStatus.OPEN
        }.length > 0 ? com.swingtrade.api.dto.PositionResponse.PositionStatus.OPEN : null);
        return response;
    }

    /**
     * Risk summary DTO.
     */
    public static class RiskSummary {
        private BigDecimal totalExposure;
        private BigDecimal availableCapital;
        private BigDecimal usedCapital;
        private BigDecimal stopLossExposure;
        private Integer numberOfPositions;
        private Map<String, Integer> sectorExposure;

        // Getters and Setters
        public BigDecimal getTotalExposure() {
            return totalExposure;
        }

        public void setTotalExposure(BigDecimal totalExposure) {
            this.totalExposure = totalExposure;
        }

        public BigDecimal getAvailableCapital() {
            return availableCapital;
        }

        public void setAvailableCapital(BigDecimal availableCapital) {
            this.availableCapital = availableCapital;
        }

        public BigDecimal getUsedCapital() {
            return usedCapital;
        }

        public void setUsedCapital(BigDecimal usedCapital) {
            this.usedCapital = usedCapital;
        }

        public BigDecimal getStopLossExposure() {
            return stopLossExposure;
        }

        public void setStopLossExposure(BigDecimal stopLossExposure) {
            this.stopLossExposure = stopLossExposure;
        }

        public Integer getNumberOfPositions() {
            return numberOfPositions;
        }

        public void setNumberOfPositions(Integer numberOfPositions) {
            this.numberOfPositions = numberOfPositions;
        }

        public Map<String, Integer> getSectorExposure() {
            return sectorExposure;
        }

        public void setSectorExposure(Map<String, Integer> sectorExposure) {
            this.sectorExposure = sectorExposure;
        }
    }
}
