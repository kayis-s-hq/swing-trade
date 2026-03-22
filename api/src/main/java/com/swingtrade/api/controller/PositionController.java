package com.swingtrade.api.controller;

import com.swingtrade.api.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for position management.
 * Provides endpoints for viewing and managing trading positions.
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private static final Logger logger = LoggerFactory.getLogger(PositionController.class);

    @Autowired
    private com.swingtrade.api.PositionService positionService;

    /**
     * Get all open positions.
     *
     * @return List of open positions
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PositionResponse>> getPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        logger.debug("Fetching positions (page: {}, size: {})", page, size);

        try {
            List<PositionResponse> positions = positionService.getOpenPositions();
            PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                    positions, page, size, (long) positions.size()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPaginatedErrorResponse("Internal Server Error", "Failed to fetch positions"));
        }
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

        try {
            PositionResponse position = positionService.getPositionBySymbol(symbol);

            if (position == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(position);

        } catch (Exception e) {
            logger.error("Error fetching position: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPositionErrorResponse("Internal Server Error", "Failed to fetch position"));
        }
    }

    /**
     * Get closed positions.
     *
     * @param page Page number
     * @param size Page size
     * @return Paginated list of closed positions
     */
    @GetMapping("/closed")
    public ResponseEntity<PaginatedResponse<PositionResponse>> getClosedPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        logger.debug("Fetching closed positions");

        try {
            List<PositionResponse> positions = positionService.getClosedPositions();
            PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                    positions, page, size, (long) positions.size()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching closed positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPaginatedErrorResponse("Internal Server Error", "Failed to fetch closed positions"));
        }
    }

    /**
     * Get positions by status.
     *
     * @param status Position status
     * @return List of positions with specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PositionResponse>> getPositionsByStatus(
            @PathVariable PositionResponse.PositionStatus status
    ) {
        logger.debug("Fetching positions with status: {}", status);

        try {
            List<PositionResponse> positions = positionService.getPositionsByStatus(status);
            return ResponseEntity.ok(positions);

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildPositionErrorResponse("Internal Server Error", "Failed to fetch positions")));
        }
    }

    /**
     * Get positions by symbol.
     *
     * @param symbol Stock symbol
     * @return List of positions for symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<PaginatedResponse<PositionResponse>> getPositionsBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        logger.debug("Fetching positions for symbol: {}", symbol);

        try {
            List<PositionResponse> positions = positionService.getPositionsBySymbol(symbol);
            PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                    positions, page, size, (long) positions.size()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPaginatedErrorResponse("Internal Server Error", "Failed to fetch positions"));
        }
    }

    /**
     * Get positions by sector.
     *
     * @param sector Stock sector
     * @return List of positions in sector
     */
    @GetMapping("/sector/{sector}")
    public ResponseEntity<List<PositionResponse>> getPositionsBySector(
            @PathVariable String sector
    ) {
        logger.debug("Fetching positions in sector: {}", sector);

        try {
            List<PositionResponse> positions = positionService.getPositionsBySector(sector);
            return ResponseEntity.ok(positions);

        } catch (Exception e) {
            logger.error("Error fetching positions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(List.of(buildPositionErrorResponse("Internal Server Error", "Failed to fetch positions")));
        }
    }

    /**
     * Get position statistics.
     *
     * @return Position statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<PositionStats> getPositionStats() {
        logger.debug("Fetching position statistics");

        try {
            PositionStats stats = positionService.getPositionStats();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error fetching position stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPositionStatsErrorResponse("Internal Server Error", "Failed to fetch stats"));
        }
    }

    /**
     * Get sector allocation.
     *
     * @return Sector allocation percentages
     */
    @GetMapping("/sector-allocation")
    public ResponseEntity<SectorAllocation> getSectorAllocation() {
        logger.debug("Fetching sector allocation");

        try {
            SectorAllocation allocation = positionService.getSectorAllocation();
            return ResponseEntity.ok(allocation);

        } catch (Exception e) {
            logger.error("Error fetching sector allocation: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildSectorAllocationErrorResponse("Internal Server Error", "Failed to fetch allocation"));
        }
    }

    /**
     * Close a position by symbol.
     *
     * @param symbol Stock symbol
     * @param request Close position request
     * @return Closed position response
     */
    @PostMapping("/{symbol}/close")
    public ResponseEntity<PositionResponse> closePosition(
            @PathVariable String symbol,
            @Valid @RequestBody(required = false) com.swingtrade.api.dto.ClosePositionRequest request
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
                    .body(buildPositionErrorResponse("Bad Request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error closing position: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildPositionErrorResponse("Internal Server Error", "Failed to close position"));
        }
    }

    /**
     * Build a PaginatedResponse with error info.
     */
    private PaginatedResponse<PositionResponse> buildPaginatedErrorResponse(String error, String message) {
        PaginatedResponse<PositionResponse> response = new PaginatedResponse<>();
        PositionResponse errorPos = buildPositionErrorResponse(error, message);
        response.setContent(List.of(errorPos));
        return response;
    }

    /**
     * Build a PositionResponse with error info.
     */
    private PositionResponse buildPositionErrorResponse(String error, String message) {
        PositionResponse response = new PositionResponse();
        response.setSymbol("ERROR");
        response.setEntryReason(error + ": " + message);
        response.setStatus(PositionResponse.PositionStatus.CLOSED);
        return response;
    }

    /**
     * Build a PositionStats with error info.
     */
    private PositionStats buildPositionStatsErrorResponse(String error, String message) {
        PositionStats stats = new PositionStats();
        stats.setMessage(error + ": " + message);
        return stats;
    }

    /**
     * Build a SectorAllocation with error info.
     */
    private SectorAllocation buildSectorAllocationErrorResponse(String error, String message) {
        SectorAllocation allocation = new SectorAllocation();
        allocation.setMessage(error + ": " + message);
        return allocation;
    }

    /**
     * Position statistics DTO.
     */
    public static class PositionStats {
        private Integer totalPositions;
        private Integer openPositions;
        private Integer closedPositions;
        private Integer stoppedOut;
        private Integer targetHit;
        private BigDecimal totalPnL;
        private BigDecimal unrealizedPnL;
        private Double winRate;
        private Double averageHoldingPeriod;
        private String message;

        // Getters and Setters
        public Integer getTotalPositions() {
            return totalPositions;
        }

        public void setTotalPositions(Integer totalPositions) {
            this.totalPositions = totalPositions;
        }

        public Integer getOpenPositions() {
            return openPositions;
        }

        public void setOpenPositions(Integer openPositions) {
            this.openPositions = openPositions;
        }

        public Integer getClosedPositions() {
            return closedPositions;
        }

        public void setClosedPositions(Integer closedPositions) {
            this.closedPositions = closedPositions;
        }

        public Integer getStoppedOut() {
            return stoppedOut;
        }

        public void setStoppedOut(Integer stoppedOut) {
            this.stoppedOut = stoppedOut;
        }

        public Integer getTargetHit() {
            return targetHit;
        }

        public void setTargetHit(Integer targetHit) {
            this.targetHit = targetHit;
        }

        public BigDecimal getTotalPnL() {
            return totalPnL;
        }

        public void setTotalPnL(BigDecimal totalPnL) {
            this.totalPnL = totalPnL;
        }

        public BigDecimal getUnrealizedPnL() {
            return unrealizedPnL;
        }

        public void setUnrealizedPnL(BigDecimal unrealizedPnL) {
            this.unrealizedPnL = unrealizedPnL;
        }

        public Double getWinRate() {
            return winRate;
        }

        public void setWinRate(Double winRate) {
            this.winRate = winRate;
        }

        public Double getAverageHoldingPeriod() {
            return averageHoldingPeriod;
        }

        public void setAverageHoldingPeriod(Double averageHoldingPeriod) {
            this.averageHoldingPeriod = averageHoldingPeriod;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Sector allocation DTO.
     */
    public static class SectorAllocation {
        private java.util.Map<String, Double> allocation;
        private BigDecimal totalExposure;
        private Integer numberOfSectors;
        private String message;

        // Getters and Setters
        public java.util.Map<String, Double> getAllocation() {
            return allocation;
        }

        public void setAllocation(java.util.Map<String, Double> allocation) {
            this.allocation = allocation;
        }

        public BigDecimal getTotalExposure() {
            return totalExposure;
        }

        public void setTotalExposure(BigDecimal totalExposure) {
            this.totalExposure = totalExposure;
        }

        public Integer getNumberOfSectors() {
            return numberOfSectors;
        }

        public void setNumberOfSectors(Integer numberOfSectors) {
            this.numberOfSectors = numberOfSectors;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
