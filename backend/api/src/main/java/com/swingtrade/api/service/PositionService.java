package com.swingtrade.api.service;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.PositionStats;
import com.swingtrade.api.dto.RiskSummary;
import com.swingtrade.api.dto.SectorAllocation;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.api.dto.TradeResponse;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.service.OrderService;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PositionStore positionStore;
    private final StockStore stockStore;
    private final PositionRepository positionRepository;
    private final TradingService tradingService;
    private final OrderService orderService;

    public PositionService(PositionStore positionStore, StockStore stockStore,
                           PositionRepository positionRepository,
                           TradingService tradingService, OrderService orderService) {
        this.positionStore = positionStore;
        this.stockStore = stockStore;
        this.positionRepository = positionRepository;
        this.tradingService = tradingService;
        this.orderService = orderService;
    }

    /**
     * Get open paper trading positions.
     *
     * <p>Reads from the DB-backed {@link PositionStore} rather than the
     * in-memory {@code TradingService} engine store, so results stay
     * consistent with the single-lookup endpoints ({@code getPositionBySymbol},
     * {@code getPositionsBySymbol}), which are already DB-backed. The
     * in-memory engine store can diverge from the DB (e.g. duplicate or
     * stale entries with no DB id) after restarts or partial persistence
     * failures, so the DB is treated as the authoritative source for listing.
     *
     * @return List of open paper positions as PositionResponse DTOs
     */
    public List<PositionResponse> getOpenPositions() {
        return positionStore.findAllOpen().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get position by ID.
     * @param id the position ID
     * @return PositionResponse details or null if not found
     */
    public PositionResponse getPositionById(Long id) {
        return positionStore.findById(id).map(this::convertToResponse).orElse(null);
    }

    /**
     * Get position by symbol.
     * @param symbol the stock symbol
     * @return PositionResponse details for the symbol, or null if not found
     */
    public PositionResponse getPositionBySymbol(String symbol) {
        // Read from the DB-backed PositionStore (same source as getOpenPositions()
        // and getRiskSummary()) rather than the in-memory TradingService engine
        // store, so this can't return a stale/orphaned in-memory position after
        // the DB-backed truth has moved on (e.g. after a close).
        Optional<Position> openPos = positionStore.findBySymbol(symbol);
        if (openPos.isPresent()) return convertToResponse(openPos.get());
        // Fall back to closed positions
        List<Position> all = positionStore.findBySymbolOrderByEntryDateDesc(symbol);
        if (!all.isEmpty()) return convertToResponse(all.get(0));
        return null;
    }

    /**
     * Get closed positions.
     * @return List of closed positions
     */
    public List<PositionResponse> getClosedPositions() {
        List<Position> closed = new ArrayList<>();
        closed.addAll(positionStore.findByStatus(PositionStatus.CLOSED));
        closed.addAll(positionStore.findByStatus(PositionStatus.STOPPED));
        closed.addAll(positionStore.findByStatus(PositionStatus.TARGET_HIT));
        return closed.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    /**
     * Get positions by status.
     * @param status the position status to filter by (as string: "OPEN", "CLOSED", "STOPPED", "TARGET_HIT")
     * @return List of positions with the given status
     */
    public List<PositionResponse> getPositionsByStatus(String status) {
        PositionStatus ps = switch (status.toUpperCase()) {
            case "OPEN" -> PositionStatus.OPEN;
            case "CLOSED" -> PositionStatus.CLOSED;
            case "STOPPED" -> PositionStatus.STOPPED;
            case "TARGET_HIT" -> PositionStatus.TARGET_HIT;
            default -> throw new IllegalArgumentException("Unknown status: " + status);
        };
        return positionStore.findByStatus(ps).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get positions by symbol (all statuses).
     * @param symbol the stock symbol
     * @return List of positions for the symbol
     */
    public List<PositionResponse> getPositionsBySymbol(String symbol) {
        return positionStore.findBySymbolOrderByEntryDateDesc(symbol).stream()
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
        List<Position> openPositions = positionStore.findAllOpen();
        List<Position> closedPositions = new ArrayList<>();
        closedPositions.addAll(positionStore.findByStatus(PositionStatus.CLOSED));
        closedPositions.addAll(positionStore.findByStatus(PositionStatus.STOPPED));
        closedPositions.addAll(positionStore.findByStatus(PositionStatus.TARGET_HIT));

        PositionStats stats = new PositionStats();
        stats.setOpenPositions(openPositions.size());
        stats.setClosedPositions(closedPositions.size());
        stats.setTotalPositions(openPositions.size() + closedPositions.size());

        BigDecimal unrealizedPnL = tradingService.getTotalUnrealizedPnL();
        BigDecimal realizedPnL = tradingService.getTotalRealizedPnL();
        BigDecimal totalPnL = realizedPnL.add(unrealizedPnL);

        stats.setTotalPnL(totalPnL);
        stats.setUnrealizedPnL(unrealizedPnL);

        long winCount = 0;
        for (Position p : closedPositions) {
            if (p.realizedPnL() != null && p.realizedPnL().compareTo(BigDecimal.ZERO) > 0) {
                winCount++;
            }
        }
        int closedCount = closedPositions.size();
        stats.setWinRate(closedCount > 0 ? (double) winCount / closedCount * 100.0 : 0.0);
        stats.setStoppedOut(0);
        stats.setTargetHit(0);

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
    @Transactional
    public PositionResponse closePosition(String symbol, String exitReason) {
        Optional<Position> posOpt = positionStore.findBySymbol(symbol);
        if (posOpt.isEmpty()) {
            return null;
        }

        Optional<PositionEntity> entityOpt = positionRepository.findById(posOpt.get().id());
        if (entityOpt.isEmpty()) {
            return null;
        }

        PositionEntity entity = entityOpt.get();
        BigDecimal exitPrice = entity.getCurrentPrice() != null ? entity.getCurrentPrice() : entity.getEntryPrice();
        String reason = exitReason != null ? exitReason : "manual_close";

        // Close in engine first (updates portfolio capital, calculates P&L)
        try {
            com.swingtrade.domain.Position enginePos =
                tradingService.findOpenPositionBySymbol(symbol);
            if (enginePos != null) {
                tradingService.closePosition(entity.getId(), exitPrice, reason);
            } else {
                logger.warn("Engine position missing for symbol {} — skipping engine close, will only update DB", symbol);
            }
        } catch (Exception e) {
            logger.warn("Engine close failed for symbol {}: {}. Proceeding with DB close only.",
                symbol, e.getMessage());
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

        BigDecimal entryPrice = request.getPrice();
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price is required and must be greater than zero");
        }

        // Auto-create stock if it doesn't exist
        if (!stockStore.existsBySymbol(request.getSymbol())) {
            StockEntity stock = new StockEntity();
            stock.setSymbol(request.getSymbol());
            stock.setName(request.getSymbol());
            stock.setExchange("NSE");
            stock.setAddedOn(LocalDate.now());
            stock.setCreatedAt(LocalDateTime.now());
            stock.setUpdatedAt(LocalDateTime.now());
            stockStore.save(stock.toDomain());
            logger.info("Auto-created stock entity for symbol: {}", request.getSymbol());
        }

        // Prevent duplicate engine positions for the same symbol
        if (tradingService.findOpenPositionBySymbol(request.getSymbol()) != null) {
            logger.info("Open position already exists for symbol: {}, reusing", request.getSymbol());
            return convertToResponse(tradingService.findOpenPositionBySymbol(request.getSymbol()));
        }

        // Create position in engine via order pipeline.
        // executePendingOrder() already creates AND persists the position
        // internally (PaperTradingEngine.createPositionFromOrder() +
        // stateService.savePosition()). We must NOT call createPositionFromOrder()
        // again here — doing so previously produced a second, orphaned
        // in-memory-only position for the same order/symbol.
        final String[] positionId = {null};
        Order order = switch (request.getDirection()) {
            case LONG -> orderService.createBuyOrder(
                request.getSymbol(), request.getQuantity(), entryPrice);
            case SHORT -> orderService.createSellOrder(
                request.getSymbol(), request.getQuantity(), entryPrice);
        };
        order = tradingService.executePendingOrder(order.getOrderId(), entryPrice);
        if (order.getStatus() == OrderStatus.FILLED) {
            com.swingtrade.domain.Position pos = tradingService.findOpenPositionBySymbol(request.getSymbol());
            if (pos == null) {
                throw new RuntimeException(
                    "Position not found after order execution for symbol " + request.getSymbol());
            }
            positionId[0] = pos.positionId();
        } else {
            throw new RuntimeException("Order not filled for symbol " + request.getSymbol() + ": status=" + order.getStatus());
        }

        // The position was already persisted by stateService.savePosition() inside executePendingOrder.
        // Just fetch the existing entity — do NOT create a second one.
        PositionEntity entity = positionRepository.findByPositionId(positionId[0])
            .orElseGet(() -> {
                logger.warn("Position {} not found in DB after engine create — falling back to symbol lookup", positionId[0]);
                return positionRepository.findOpenBySymbol(request.getSymbol())
                    .orElseGet(() -> {
                        PositionEntity e = new PositionEntity();
                        e.setSymbol(request.getSymbol());
                        e.setEntryPrice(entryPrice);
                        e.setQuantity(request.getQuantity());
                        e.setEntryDate(LocalDate.now());
                        e.setEntryReason(request.getEntryReason());
                        e.setStatus("OPEN");
                        e.setCurrentPrice(entryPrice);
                        e.setCreatedAt(LocalDateTime.now());
                        e.setUpdatedAt(LocalDateTime.now());
                        if (request.getStopPrice() != null) e.setStopLoss(request.getStopPrice());
                        if (request.getTarget() != null) e.setTarget(request.getTarget());
                        return e;
                    });
            });

        // Guard against orphaned rows: any entity reaching this point (freshly built,
        // or matched by symbol from a legacy/partial write) must carry the engine's
        // positionId so PaperTradingEngine.closePosition(Long) can resolve it back to
        // the in-memory position later. Without this, close-by-DB-id lookups NPE.
        if ((entity.getPositionId() == null || entity.getPositionId().isBlank()) && positionId[0] != null) {
            entity.setPositionId(positionId[0]);
        }

        PositionEntity savedEntity = positionRepository.save(entity);
        logger.info("Position for symbol: {} at price: {} (engine position: {})",
            request.getSymbol(), entryPrice, positionId[0]);

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
        // Use the DB-backed store (same source as getOpenPositions()) to avoid
        // double-counting exposure from stale/duplicate in-memory engine entries.
        List<Position> openPositions = positionStore.findAllOpen();
        RiskSummary summary = new RiskSummary();

        BigDecimal totalExposure = openPositions.stream()
                .filter(p -> p.currentPrice() != null && p.quantity() != null)
                .map(p -> p.currentPrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalExposure(totalExposure);
        summary.setNumberOfPositions(openPositions.size());

        BigDecimal availableCapital = tradingService.getCurrentCash();
        summary.setAvailableCapital(availableCapital);
        summary.setUsedCapital(totalExposure);

        BigDecimal stopLossExposure = openPositions.stream()
                .filter(p -> p.stopLoss() != null && p.entryPrice() != null && p.quantity() != null)
                .map(p -> p.entryPrice().subtract(p.stopLoss())
                        .multiply(BigDecimal.valueOf(p.quantity())))
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
     * Convert Position domain record to PositionResponse DTO.
     */
    private PositionResponse convertToResponse(Position position) {
        return new PositionResponse(position);
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

    }
