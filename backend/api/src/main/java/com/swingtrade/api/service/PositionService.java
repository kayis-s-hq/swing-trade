package com.swingtrade.api.service;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PositionStats;
import com.swingtrade.api.dto.RiskSummary;
import com.swingtrade.api.dto.SectorAllocation;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.api.dto.TradeResponse;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.OrderStatus;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.domain.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final StockRepository stockRepository;
    private final PaperTradingEngine paperTradingEngine;
    private final OrderManager orderManager;
    private final PositionManager positionManager;

    public PositionService(PositionRepository positionRepository, StockRepository stockRepository,
                           PaperTradingEngine paperTradingEngine, OrderManager orderManager,
                           PositionManager positionManager) {
        this.positionRepository = positionRepository;
        this.stockRepository = stockRepository;
        this.paperTradingEngine = paperTradingEngine;
        this.orderManager = orderManager;
        this.positionManager = positionManager;
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
     * @param status the position status to filter by (as string: "OPEN", "CLOSED", "STOPPED", "TARGET_HIT")
     * @return List of positions with the given status
     */
    public List<PositionResponse> getPositionsByStatus(String status) {
        // Convert string to uppercase for comparison
        String statusUpper = status.toUpperCase();
        List<PositionEntity> allPositions = positionRepository.findAll();
        return allPositions.stream()
                .filter(p -> statusUpper.equals(p.getStatus().toUpperCase()))
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
    public PositionStats getPositionStats() {
        List<PositionEntity> allPositions = positionRepository.findAll();
        List<PositionEntity> openPositions = positionRepository.findAllOpenPositions();

        PositionStats stats = new PositionStats();
        stats.setTotalPositions(allPositions.size());
        stats.setOpenPositions(openPositions.size());
        stats.setClosedPositions(allPositions.size() - openPositions.size());

        // Single-pass calculation of all stats
        BigDecimal totalPnL = BigDecimal.ZERO;
        BigDecimal unrealizedPnL = BigDecimal.ZERO;
        long stoppedCount = 0;
        long targetHitCount = 0;
        long winCount = 0;
        long closedCount = 0;

        for (PositionEntity p : allPositions) {
            String status = p.getStatus();
            if ("OPEN".equals(status)) {
                if (p.getCurrentPrice() != null && p.getEntryPrice() != null && p.getQuantity() != null) {
                    unrealizedPnL = unrealizedPnL.add(
                        p.getCurrentPrice().subtract(p.getEntryPrice())
                            .multiply(BigDecimal.valueOf(p.getQuantity())));
                }
            } else {
                closedCount++;
                if ("STOPPED".equals(status)) stoppedCount++;
                if ("TARGET_HIT".equals(status)) targetHitCount++;
                if (p.getPnl() != null && p.getPnl().compareTo(BigDecimal.ZERO) > 0) winCount++;
                if (p.getCurrentPrice() != null && p.getEntryPrice() != null && p.getQuantity() != null) {
                    totalPnL = totalPnL.add(
                        p.getCurrentPrice().subtract(p.getEntryPrice())
                            .multiply(BigDecimal.valueOf(p.getQuantity())));
                }
            }
        }

        stats.setTotalPnL(totalPnL);
        stats.setUnrealizedPnL(unrealizedPnL);
        stats.setStoppedOut((int) stoppedCount);
        stats.setTargetHit((int) targetHitCount);
        stats.setWinRate(closedCount > 0 ? (double) winCount / closedCount * 100.0 : 0.0);

        return stats;
    }

    /**
     * Get sector allocation.
     * @return Sector allocation data (currently empty - sector data not yet available)
     */
    public SectorAllocation getSectorAllocation() {
        SectorAllocation allocation = new SectorAllocation();
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
        BigDecimal exitPrice = entity.getCurrentPrice() != null ? entity.getCurrentPrice() : entity.getEntryPrice();
        String reason = exitReason != null ? exitReason : "manual_close";

        // Close in engine first (updates portfolio capital, calculates P&L)
        try {
            com.swingtrade.broker.model.Position enginePos =
                paperTradingEngine.findOpenPositionBySymbol(symbol);
            if (enginePos == null) {
                throw new RuntimeException("No open position found in engine for symbol: " + symbol);
            }
            paperTradingEngine.closePosition(enginePos.getPositionId(), exitPrice, reason);
        } catch (Exception e) {
            logger.error("PaperTradingEngine failed to close position {} for symbol {}: {}",
                entity.getId(), symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to close position in engine: " + e.getMessage(), e);
        }

        // Then update DB entity
        entity.setStatus("CLOSED");
        entity.setCurrentPrice(exitPrice);
        entity.setUpdatedAt(LocalDateTime.now());
        PositionEntity savedEntity = positionRepository.save(entity);

        return convertToResponse(savedEntity);
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

        // Auto-create stock if it doesn't exist
        if (!stockRepository.existsBySymbol(request.getSymbol())) {
            StockEntity stock = new StockEntity();
            stock.setSymbol(request.getSymbol());
            stock.setName(request.getSymbol());
            stock.setExchange("NSE");
            stock.setAddedOn(LocalDate.now());
            stock.setCreatedAt(LocalDateTime.now());
            stock.setUpdatedAt(LocalDateTime.now());
            stockRepository.save(stock);
            logger.info("Auto-created stock entity for symbol: {}", request.getSymbol());
        }

        // Create position in engine via order pipeline
        String positionId = null;
        Order order = orderManager.createBuyOrder(
            request.getSymbol(), request.getQuantity(), entryPrice);
        order = paperTradingEngine.executePendingOrder(order.getOrderId(), entryPrice);
        if (order.getStatus() == OrderStatus.FILLED) {
            com.swingtrade.broker.model.Position pos = paperTradingEngine.createPositionFromOrder(order);
            positionId = pos.getPositionId();
        } else {
            throw new RuntimeException("Order not filled for symbol " + request.getSymbol() + ": status=" + order.getStatus());
        }

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
        if (request.getTarget() != null) {
            entity.setTarget(request.getTarget());
        }

        PositionEntity savedEntity = positionRepository.save(entity);
        logger.info("Created new position for symbol: {} at price: {} (engine position: {})",
            request.getSymbol(), entryPrice, positionId);

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
    public RiskSummary getRiskSummary() {
        List<PositionEntity> openPositions = positionRepository.findAllOpenPositions();
        RiskSummary summary = new RiskSummary();

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
        response.setExitReason(null);
        return response;
    }

    public PositionManager getPositionManager() {
        return positionManager;
    }
}
