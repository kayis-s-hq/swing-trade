package com.swingtrade.api;

import com.swingtrade.api.dto.ClosePositionRequest;
import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PositionStats;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.api.dto.TradeResponse;
import com.swingtrade.api.dto.SectorAllocation;
import com.swingtrade.api.dto.RiskSummary;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.domain.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing paper trading positions.
 * Provides CRUD operations on positions, delegating to the repository layer
 * and PaperTradingEngine for trade execution.
 */
@Service
public class PositionService {

    private static final Logger logger = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positionRepository;
    private final PaperTradingEngine paperTradingEngine;

    @Autowired
    public PositionService(PositionRepository positionRepository, PaperTradingEngine paperTradingEngine) {
        this.positionRepository = positionRepository;
        this.paperTradingEngine = paperTradingEngine;
    }

    /**
     * Get open paper trading positions.
     * @return List of open paper positions as PositionResponse DTOs
     */
    public List<PositionResponse> getOpenPositions() {
        List<PositionEntity> entities = positionRepository.findAllOpenPositions();
        return entities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get position by ID.
     * @param id the position ID
     * @return PositionResponse details or null if not found
     */
    public PositionResponse getPositionById(Long id) {
        Optional<PositionEntity> entity = positionRepository.findById(id);
        return entity.map(this::convertToResponse).orElse(null);
    }

    /**
     * Get position by symbol.
     * @param symbol the stock symbol
     * @return PositionResponse details for the symbol, or null if not found
     */
    public PositionResponse getPositionBySymbol(String symbol) {
        Optional<PositionEntity> entity = positionRepository.findOpenBySymbol(symbol);
        return entity.map(this::convertToResponse).orElse(null);
    }

    /**
     * Get closed positions.
     * @return List of closed positions
     */
    public List<PositionResponse> getClosedPositions() {
        // Retrieve all positions and filter for closed ones
        List<PositionEntity> allPositions = positionRepository.findAll();
        return allPositions.stream()
                .filter(p -> !"OPEN".equals(p.getStatus()))
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get positions by status.
     * @param status the position status to filter by
     * @return List of positions with the given status
     */
    public List<PositionResponse> getPositionsByStatus(PositionResponse.PositionStatus status) {
        List<PositionEntity> allPositions = positionRepository.findAll();
        return allPositions.stream()
                .filter(p -> status.name().equals(p.getStatus()))
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get positions by symbol (all statuses).
     * @param symbol the stock symbol
     * @return List of positions for the symbol
     */
    public List<PositionResponse> getPositionsBySymbol(String symbol) {
        List<PositionEntity> entities = positionRepository.findBySymbolOrderByEntryDateDesc(symbol);
        return entities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get positions by sector.
     * @param sector the sector name
     * @return List of positions in the sector (currently returns empty - sector data not yet available)
     */
    public List<PositionResponse> getPositionsBySector(String sector) {
        // Sector data is not available on PositionEntity yet
        // Return empty list until sector mapping is implemented
        return Collections.emptyList();
    }

    /**
     * Get position statistics.
     * @return Position statistics
     */
    public PositionService.PositionStats getPositionStats() {
        List<PositionEntity> allPositions = positionRepository.findAll();
        List<PositionEntity> openPositions = positionRepository.findAllOpenPositions();

        PositionService.PositionStats stats = new PositionService.PositionStats();
        stats.setTotalPositions(allPositions.size());
        stats.setOpenPositions(openPositions.size());
        stats.setClosedPositions(allPositions.size() - openPositions.size());

        // Calculate total P&L from closed positions
        BigDecimal totalPnL = allPositions.stream()
                .filter(p -> !"OPEN".equals(p.getStatus()) && p.getCurrentPrice() != null && p.getEntryPrice() != null)
                .map(p -> p.getCurrentPrice().subtract(p.getEntryPrice())
                        .multiply(BigDecimal.valueOf(p.getQuantity() != null ? p.getQuantity() : 0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalPnL(totalPnL);

        // Calculate unrealized P&L from open positions
        BigDecimal unrealizedPnL = openPositions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getEntryPrice() != null)
                .map(p -> p.getCurrentPrice().subtract(p.getEntryPrice())
                        .multiply(BigDecimal.valueOf(p.getQuantity() != null ? p.getQuantity() : 0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setUnrealizedPnL(unrealizedPnL);

        // Count stopped and target-hit positions
        long stoppedCount = allPositions.stream().filter(p -> "STOPPED".equals(p.getStatus())).count();
        long targetHitCount = allPositions.stream().filter(p -> "TARGET_HIT".equals(p.getStatus())).count();
        stats.setStoppedOut((int) stoppedCount);
        stats.setTargetHit((int) targetHitCount);

        // Win rate
        long closedCount = allPositions.size() - openPositions.size();
        if (closedCount > 0) {
            stats.setWinRate((double) targetHitCount / closedCount * 100.0);
        } else {
            stats.setWinRate(0.0);
        }

        return stats;
    }

    /**
     * Get sector allocation.
     * @return Sector allocation data (currently empty - sector data not yet available)
     */
    public PositionService.SectorAllocation getSectorAllocation() {
        PositionService.SectorAllocation allocation = new PositionService.SectorAllocation();
        allocation.setNumberOfSectors(0);
        allocation.setTotalExposure(BigDecimal.ZERO);
        allocation.setAllocation(Collections.emptyMap());
        return allocation;
    }

    /**
     * Close a position by symbol.
     * @param symbol the stock symbol
     * @param exitReason the reason for closing
     * @return Closed position response or null if not found
     */
    public PositionResponse closePosition(String symbol, String exitReason) {
        Optional<PositionEntity> entityOpt = positionRepository.findOpenBySymbol(symbol);
        if (entityOpt.isEmpty()) {
            return null;
        }

        PositionEntity entity = entityOpt.get();

        // Close position in paper trading engine using symbol-based close
        BigDecimal exitPrice = entity.getCurrentPrice() != null ? entity.getCurrentPrice() : entity.getEntryPrice();
        String reason = exitReason != null ? exitReason : "manual_close";

        try {
            // Attempt to close via PaperTradingEngine if position exists there
            paperTradingEngine.closePosition("POS_" + entity.getId(), exitPrice, reason);
        } catch (Exception e) {
            logger.warn("Could not close position in PaperTradingEngine: {}", e.getMessage());
        }

        // Update entity status
        entity.setStatus("CLOSED");
        entity.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(entity);

        return convertToResponse(entity);
    }

    /**
     * Create a new position from a trade request.
     * @param request the trade request containing position details
     * @return Created position response
     */
    public PositionResponse createPosition(TradeRequest request) {
        if (request.getSymbol() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid trade request: symbol and positive quantity required");
        }

        BigDecimal entryPrice = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;

        PositionEntity entity = new PositionEntity();
        entity.setSymbol(request.getSymbol());
        entity.setEntryPrice(entryPrice);
        entity.setQuantity(request.getQuantity());
        entity.setEntryDate(LocalDate.now());
        entity.setEntryReason(request.getEntryReason());
        entity.setStatus("OPEN");
        entity.setCurrentPrice(entryPrice);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        if (request.getStopPrice() != null) {
            entity.setStopLoss(request.getStopPrice());
        }

        PositionEntity savedEntity = positionRepository.save(entity);
        logger.info("Created new position for symbol: {} at price: {}", request.getSymbol(), entryPrice);

        return convertToResponse(savedEntity);
    }

    /**
     * Get trade history for a symbol.
     * @param symbol the stock symbol
     * @return List of trade responses for the symbol
     */
    public List<TradeResponse> getTradeHistory(String symbol) {
        List<PositionEntity> entities = positionRepository.findBySymbolOrderByEntryDateDesc(symbol);
        return entities.stream()
                .filter(p -> !"OPEN".equals(p.getStatus()))
                .map(this::convertToTradeResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get risk summary for current positions.
     * @return Risk summary
     */
    public PositionService.RiskSummary getRiskSummary() {
        List<PositionEntity> openPositions = positionRepository.findAllOpenPositions();
        PositionService.RiskSummary summary = new PositionService.RiskSummary();

        BigDecimal totalExposure = openPositions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getQuantity() != null)
                .map(p -> p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalExposure(totalExposure);
        summary.setNumberOfPositions(openPositions.size());

        BigDecimal availableCapital = paperTradingEngine.getCurrentCash();
        summary.setAvailableCapital(availableCapital);
        summary.setUsedCapital(totalExposure);

        BigDecimal stopLossExposure = openPositions.stream()
                .filter(p -> p.getStopLoss() != null && p.getEntryPrice() != null && p.getQuantity() != null)
                .map(p -> p.getEntryPrice().subtract(p.getStopLoss())
                        .multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setStopLossExposure(stopLossExposure);

        return summary;
    }

    /**
     * Convert PositionEntity to PositionResponse DTO.
     */
    private PositionResponse convertToResponse(PositionEntity entity) {
        Position domainPosition = entity.toDomain();
        return new PositionResponse(domainPosition);
    }

    /**
     * Convert PositionEntity to TradeResponse DTO.
     */
    private TradeResponse convertToTradeResponse(PositionEntity entity) {
        TradeResponse response = new TradeResponse();
        response.setSymbol(entity.getSymbol());
        response.setEntryPrice(entity.getEntryPrice());
        response.setEntryDate(entity.getEntryDate());
        response.setQuantity(entity.getQuantity());
        response.setExitReason(entity.getEntryReason());
        return response;
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
        private java.math.BigDecimal totalPnL;
        private java.math.BigDecimal unrealizedPnL;
        private Double winRate;
        private Double averageHoldingPeriod;
        private String message;

        public PositionStats() {}

        public PositionStats(Integer totalPositions, Integer openPositions, Integer closedPositions,
                            Integer stoppedOut, Integer targetHit, java.math.BigDecimal totalPnL,
                            java.math.BigDecimal unrealizedPnL, Double winRate, Double averageHoldingPeriod) {
            this.totalPositions = totalPositions;
            this.openPositions = openPositions;
            this.closedPositions = closedPositions;
            this.stoppedOut = stoppedOut;
            this.targetHit = targetHit;
            this.totalPnL = totalPnL;
            this.unrealizedPnL = unrealizedPnL;
            this.winRate = winRate;
            this.averageHoldingPeriod = averageHoldingPeriod;
        }

        public Integer getTotalPositions() { return totalPositions; }
        public void setTotalPositions(Integer totalPositions) { this.totalPositions = totalPositions; }

        public Integer getOpenPositions() { return openPositions; }
        public void setOpenPositions(Integer openPositions) { this.openPositions = openPositions; }

        public Integer getClosedPositions() { return closedPositions; }
        public void setClosedPositions(Integer closedPositions) { this.closedPositions = closedPositions; }

        public Integer getStoppedOut() { return stoppedOut; }
        public void setStoppedOut(Integer stoppedOut) { this.stoppedOut = stoppedOut; }

        public Integer getTargetHit() { return targetHit; }
        public void setTargetHit(Integer targetHit) { this.targetHit = targetHit; }

        public java.math.BigDecimal getTotalPnL() { return totalPnL; }
        public void setTotalPnL(java.math.BigDecimal totalPnL) { this.totalPnL = totalPnL; }

        public java.math.BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
        public void setUnrealizedPnL(java.math.BigDecimal unrealizedPnL) { this.unrealizedPnL = unrealizedPnL; }

        public Double getWinRate() { return winRate; }
        public void setWinRate(Double winRate) { this.winRate = winRate; }

        public Double getAverageHoldingPeriod() { return averageHoldingPeriod; }
        public void setAverageHoldingPeriod(Double averageHoldingPeriod) { this.averageHoldingPeriod = averageHoldingPeriod; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Sector allocation DTO.
     */
    public static class SectorAllocation {
        private java.util.Map<String, Double> allocation;
        private java.math.BigDecimal totalExposure;
        private Integer numberOfSectors;
        private String message;

        public SectorAllocation() {}

        public SectorAllocation(java.util.Map<String, Double> allocation, java.math.BigDecimal totalExposure, Integer numberOfSectors) {
            this.allocation = allocation;
            this.totalExposure = totalExposure;
            this.numberOfSectors = numberOfSectors;
        }

        public java.util.Map<String, Double> getAllocation() { return allocation; }
        public void setAllocation(java.util.Map<String, Double> allocation) { this.allocation = allocation; }

        public java.math.BigDecimal getTotalExposure() { return totalExposure; }
        public void setTotalExposure(java.math.BigDecimal totalExposure) { this.totalExposure = totalExposure; }

        public Integer getNumberOfSectors() { return numberOfSectors; }
        public void setNumberOfSectors(Integer numberOfSectors) { this.numberOfSectors = numberOfSectors; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Risk summary DTO.
     */
    public static class RiskSummary {
        private java.math.BigDecimal totalExposure;
        private java.math.BigDecimal availableCapital;
        private java.math.BigDecimal usedCapital;
        private java.math.BigDecimal stopLossExposure;
        private Integer numberOfPositions;
        private java.util.Map<String, Integer> sectorExposure;

        public RiskSummary() {}

        public java.math.BigDecimal getTotalExposure() { return totalExposure; }
        public void setTotalExposure(java.math.BigDecimal totalExposure) { this.totalExposure = totalExposure; }

        public java.math.BigDecimal getAvailableCapital() { return availableCapital; }
        public void setAvailableCapital(java.math.BigDecimal availableCapital) { this.availableCapital = availableCapital; }

        public java.math.BigDecimal getUsedCapital() { return usedCapital; }
        public void setUsedCapital(java.math.BigDecimal usedCapital) { this.usedCapital = usedCapital; }

        public java.math.BigDecimal getStopLossExposure() { return stopLossExposure; }
        public void setStopLossExposure(java.math.BigDecimal stopLossExposure) { this.stopLossExposure = stopLossExposure; }

        public Integer getNumberOfPositions() { return numberOfPositions; }
        public void setNumberOfPositions(Integer numberOfPositions) { this.numberOfPositions = numberOfPositions; }

        public java.util.Map<String, Integer> getSectorExposure() { return sectorExposure; }
        public void setSectorExposure(java.util.Map<String, Integer> sectorExposure) { this.sectorExposure = sectorExposure; }
    }
}
