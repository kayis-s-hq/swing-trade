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
        List<PositionResponse> positions = positionService.getOpenPositions();
        PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                positions, page, size, (long) positions.size()
        );
        return ResponseEntity.ok(response);
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
        List<PositionResponse> positions = positionService.getClosedPositions();
        PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                positions, page, size, (long) positions.size()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Get positions by status.
     *
     * @param status Position status
     * @return List of positions with specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PositionResponse>> getPositionsByStatus(
            @PathVariable String status
    ) {
        logger.debug("Fetching positions with status: {}", status);
        List<PositionResponse> positions = positionService.getPositionsByStatus(status);
        return ResponseEntity.ok(positions);
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
        List<PositionResponse> positions = positionService.getPositionsBySymbol(symbol);
        PaginatedResponse<PositionResponse> response = new PaginatedResponse<>(
                positions, page, size, (long) positions.size()
        );
        return ResponseEntity.ok(response);
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
        List<PositionResponse> positions = positionService.getPositionsBySector(sector);
        return ResponseEntity.ok(positions);
    }

    /**
     * Get position statistics.
     *
     * @return Position statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<com.swingtrade.api.PositionService.PositionStats> getPositionStats() {
        logger.debug("Fetching position statistics");
        com.swingtrade.api.PositionService.PositionStats stats = positionService.getPositionStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get sector allocation.
     *
     * @return Sector allocation percentages
     */
    @GetMapping("/sector-allocation")
    public ResponseEntity<com.swingtrade.api.PositionService.SectorAllocation> getSectorAllocation() {
        logger.debug("Fetching sector allocation");
        com.swingtrade.api.PositionService.SectorAllocation allocation = positionService.getSectorAllocation();
        return ResponseEntity.ok(allocation);
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
        PositionResponse position = positionService.closePosition(symbol,
                request != null ? request.getExitReason() : null);

        if (position == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(position);
    }
}
